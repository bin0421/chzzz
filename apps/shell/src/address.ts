// 서버 주소 규칙(스펙): https://호스트[:포트], 또는 같은 PC의 http://localhost·127.0.0.1만 허용하고 출처만 저장한다.
export type ParsedServerAddress = { ok: true; origin: string } | { ok: false; reason: string }

const LOCAL_HTTP_HOSTS = new Set(['localhost', '127.0.0.1'])

export function parseServerAddress(input: string): ParsedServerAddress {
  const text = input.trim()
  if (text === '') return fail('서버 주소를 입력하세요.')
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(text)) {
    return fail('주소는 https:// 또는 http://로 시작해야 합니다.')
  }

  let url: URL
  try {
    url = new URL(text)
  } catch {
    return fail('주소 형식이 올바르지 않습니다.')
  }
  if (url.protocol !== 'https:' && url.protocol !== 'http:') {
    return fail('지원하지 않는 주소 형식입니다. https:// 또는 http://localhost만 쓸 수 있습니다.')
  }
  if (url.username !== '' || url.password !== '') {
    return fail('주소에 사용자 정보(아이디·비밀번호)를 넣을 수 없습니다.')
  }
  if (url.protocol === 'http:' && !LOCAL_HTTP_HOSTS.has(url.hostname)) {
    return fail('http 주소는 localhost·127.0.0.1만 쓸 수 있습니다. 다른 기기의 서버는 https://로 입력하세요.')
  }
  return { ok: true, origin: url.origin }
}

function fail(reason: string): ParsedServerAddress {
  return { ok: false, reason }
}
