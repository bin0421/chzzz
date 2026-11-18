// 5단계 상태 유지 확인 스크립트: 백엔드 대수가 바뀌는 동안 로그인 상태·방송 목록·댓글이 그대로인지 본다.
//
//   1. 회원가입 → 로그인(세션 쿠키·XSRF-TOKEN 쿠키를 받는다)
//   2. 녹화방송(외부 URL) 등록 → 댓글 1 작성
//   3. 같은 쿠키로 INTERVAL초마다 /api/auth/me, 방송 목록, 댓글 목록을 조회해
//      시각, 응답한 백엔드 파드(X-Backend-Instance), 결과를 한 줄로 찍는다.
//   4. 중간(DURATION의 절반)에 댓글 2를 쓰고, 그 뒤 조회에서 쓴 파드가 아닌 다른 파드도 댓글 2를 보여 주는지 확인한다.
//   하나라도 어긋나면 FAIL을 찍고, 끝날 때 0이 아닌 종료 코드(99, 체크 실패)로 끝난다.
//
// 실행(저장소 루트, PowerShell·bash 같음). 부하 스크립트와 다른 터미널에서 함께 돌린다:
//   docker run --rm -i -v "${PWD}/load:/load" grafana/k6:2.3.0 run -q --log-format=raw /load/state-check.js
//
// 환경변수
//   BASE_URL  대상 주소. 기본값 http://host.docker.internal:8000
//   DURATION  조회를 계속할 초. 기본값 420(7분: 스케일 아웃 → 부하 중지 → 스케일 인까지 덮는다)
//   INTERVAL  조회 간격 초. 기본값 5
import http from 'k6/http';
import { check, sleep, fail } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8000').replace(/\/$/, '');
const DURATION = Number(__ENV.DURATION || '420');
const INTERVAL = Number(__ENV.INTERVAL || '5');

export const options = {
  // 가상 사용자 1명이 한 번만 돈다. 기본 최대 실행 시간(10분)보다 길게 돌릴 수 있게 DURATION에 맞춘다.
  scenarios: {
    check: { executor: 'per-vu-iterations', vus: 1, iterations: 1, maxDuration: `${DURATION + 120}s` },
  },
  // 체크가 하나라도 실패하면 k6가 종료 코드 99로 끝난다.
  thresholds: { checks: ['rate==1'] },
};

function now() {
  return new Date().toISOString().substring(11, 19); // UTC HH:MM:SS
}

function pod(res) {
  return res.headers['X-Backend-Instance'] || '-';
}

// 상태를 바꾸는 요청에는 쿠키의 CSRF 토큰을 헤더로 함께 보낸다(ADR-0005, 어느 파드든 쿠키 값과 비교만 한다).
function csrfHeaders(extra) {
  const token = http.cookieJar().cookiesForURL(BASE_URL)['XSRF-TOKEN'];
  return Object.assign({ 'X-XSRF-TOKEN': token ? token[0] : '' }, extra || {});
}

function postJson(path, body) {
  return http.post(`${BASE_URL}${path}`, JSON.stringify(body), {
    headers: csrfHeaders({ 'Content-Type': 'application/json' }),
  });
}

function must(res, expected, what) {
  const ok = check(res, { [what]: (r) => r.status === expected });
  console.log(`${now()} ${ok ? 'OK  ' : 'FAIL'} ${what}: ${res.status} @ ${pod(res)}`);
  if (!ok) fail(`${what} 실패: ${res.status} ${res.body}`);
  return res;
}

export default function () {
  // CSRF 쿠키를 먼저 받는다(모든 응답에 실린다). 아직 로그인 전이라 401이 정상이다.
  http.get(`${BASE_URL}/api/auth/me`, { responseCallback: http.expectedStatuses(401) });

  const suffix = `${Date.now().toString(36)}${Math.floor(Math.random() * 1000)}`;
  const loginId = `sc_${suffix}`.substring(0, 20);
  const password = 'state-check-pass';
  const nickname = `확인${suffix}`.substring(0, 20);

  must(postJson('/api/auth/signup', { loginId, password, nickname }), 201, `회원가입 ${loginId}`);
  must(postJson('/api/auth/login', { loginId, password }), 200, '로그인');

  const created = must(
    http.post(
      `${BASE_URL}/api/recordings`,
      { title: `상태 확인 ${suffix}`, url: 'https://example.com/state-check.mp4' },
      { headers: csrfHeaders() },
    ),
    201,
    '방송 등록',
  );
  const recordingId = created.json().id;
  const firstComment = `댓글 1 (${suffix})`;
  must(postJson(`/api/recordings/${recordingId}/comments`, { content: firstComment }), 201, `댓글 1 작성 (방송 ${recordingId})`);

  const expected = [firstComment];
  const midpoint = DURATION / 2;
  let secondWriter = null;
  const podsSeen = new Set();
  const podsSeenSecond = new Set();

  console.log(`시각     | me 파드 / 결과 | 목록 파드 / 결과 | 댓글 파드 / 결과`);
  const start = Date.now();
  while ((Date.now() - start) / 1000 < DURATION) {
    if (secondWriter === null && (Date.now() - start) / 1000 >= midpoint) {
      const secondComment = `댓글 2 (${suffix})`;
      const res = must(postJson(`/api/recordings/${recordingId}/comments`, { content: secondComment }), 201, '댓글 2 작성');
      secondWriter = pod(res);
      expected.push(secondComment);
    }

    const me = http.get(`${BASE_URL}/api/auth/me`);
    const meOk = check(me, { '로그인 유지(/me 200, 닉네임 같음)': (r) => r.status === 200 && r.json('nickname') === nickname });

    const list = http.get(`${BASE_URL}/api/recordings`);
    const listOk = check(list, {
      '방송 목록에 등록한 방송': (r) => r.status === 200 && r.json().some((x) => x.id === recordingId),
    });

    const comments = http.get(`${BASE_URL}/api/recordings/${recordingId}/comments`);
    const contents = comments.status === 200 ? comments.json().map((c) => c.content) : [];
    const commentsOk = check(comments, {
      '댓글 목록이 쓴 댓글과 같음': () => contents.length === expected.length && expected.every((e) => contents.includes(e)),
    });

    [me, list, comments].forEach((r) => podsSeen.add(pod(r)));
    if (secondWriter !== null) podsSeenSecond.add(pod(comments));

    const mark = (ok) => (ok ? 'OK' : 'FAIL');
    console.log(
      `${now()} | ${pod(me)} ${mark(meOk)} | ${pod(list)} ${mark(listOk)} | ${pod(comments)} ${mark(commentsOk)} 댓글 ${contents.length}개`,
    );
    sleep(INTERVAL);
  }

  const othersSawSecond = [...podsSeenSecond].filter((p) => p !== secondWriter);
  check(null, { '댓글 2를 쓴 파드가 아닌 파드에서도 보임': () => othersSawSecond.length > 0 });
  console.log(`응답한 파드 ${podsSeen.size}개: ${[...podsSeen].sort().join(', ')}`);
  console.log(`댓글 2 작성 파드: ${secondWriter}, 그 뒤 댓글 2를 보여 준 다른 파드: ${othersSawSecond.sort().join(', ') || '(없음)'}`);
}
