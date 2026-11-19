// 미디어(카메라·마이크)와 영상 전체 화면만, 저장한 서버 출처에서 요청할 때만 허용한다. 나머지는 모두 거절한다.
export interface PermissionRequest {
  permission: string
  requestingUrl: string
  serverOrigin: string | null
}

const ALLOWED_FOR_SERVER = new Set(['media', 'fullscreen'])

export function isPermissionAllowed({ permission, requestingUrl, serverOrigin }: PermissionRequest): boolean {
  if (serverOrigin === null || !ALLOWED_FOR_SERVER.has(permission)) return false
  return originOf(requestingUrl) === serverOrigin
}

function originOf(url: string): string | null {
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}
