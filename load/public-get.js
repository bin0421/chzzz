// 5단계 부하 스크립트: 진입 주소(frontend nginx → backend Service)의 공개 GET을 계속 불러 백엔드 CPU를 올린다.
// 로그인 없이 부르는 조회만 쓴다: 녹화·라이브 방송 목록과 상세, 방송별 댓글·후원 목록.
//
// 실행(저장소 루트, PowerShell·bash 같음. 설치 없이 Docker만 있으면 된다):
//   docker run --rm -i -v "${PWD}/load:/load" grafana/k6:2.3.0 run /load/public-get.js
// 세기·시간·대상 바꾸기(-e 이름=값):
//   docker run --rm -i -v "${PWD}/load:/load" -e VUS=40 -e DURATION=5m grafana/k6:2.3.0 run /load/public-get.js
//
// 환경변수
//   BASE_URL  대상 주소. 기본값 http://host.docker.internal:8000 (컨테이너 안에서 본 호스트의 localhost:8000)
//   VUS       동시 가상 사용자 수. 기본값 30
//   DURATION  부하 시간(k6 형식: 30s, 3m). 기본값 3m
//   SLEEP     가상 사용자가 한 바퀴(요청 8개)를 돈 뒤 쉬는 초. 기본값 0.1
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = (__ENV.BASE_URL || 'http://host.docker.internal:8000').replace(/\/$/, '');
const SLEEP = Number(__ENV.SLEEP || '0.1');

export const options = {
  vus: Number(__ENV.VUS || '30'),
  duration: __ENV.DURATION || '3m',
  // 방송이 하나도 없어도 목록 조회는 200이어야 한다. 실패율이 1%를 넘으면 k6가 0이 아닌 코드로 끝난다.
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

// 시작할 때 한 번 목록을 받아 상세·댓글·후원 조회에 쓸 방송 ID를 정한다.
export function setup() {
  const recordings = http.get(`${BASE_URL}/api/recordings`);
  const lives = http.get(`${BASE_URL}/api/live-broadcasts`);
  if (recordings.status !== 200 || lives.status !== 200) {
    throw new Error(`대상에 닿지 않습니다: ${BASE_URL} (녹화 ${recordings.status}, 라이브 ${lives.status})`);
  }
  return {
    recordingIds: recordings.json().map((r) => r.id),
    liveIds: lives.json().map((l) => l.id),
  };
}

function pick(ids) {
  return ids.length === 0 ? null : ids[Math.floor(Math.random() * ids.length)];
}

// 방송 종류(recordings 또는 live-broadcasts)별로 목록·상세·댓글·후원을 차례로 부른다.
function browse(kind, id) {
  const requests = [['GET', `${BASE_URL}/api/${kind}`, null, { tags: { name: `/api/${kind}` } }]];
  if (id !== null) {
    requests.push(
      ['GET', `${BASE_URL}/api/${kind}/${id}`, null, { tags: { name: `/api/${kind}/{id}` } }],
      ['GET', `${BASE_URL}/api/${kind}/${id}/comments`, null, { tags: { name: `/api/${kind}/{id}/comments` } }],
      ['GET', `${BASE_URL}/api/${kind}/${id}/donations`, null, { tags: { name: `/api/${kind}/{id}/donations` } }],
    );
  }
  for (const res of http.batch(requests)) {
    check(res, { '200 응답': (r) => r.status === 200 });
  }
}

export default function (data) {
  browse('recordings', pick(data.recordingIds));
  browse('live-broadcasts', pick(data.liveIds));
  sleep(SLEEP);
}
