import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  // 송출 화면이 탭 저장소에 남기는 송출 키가 다음 테스트로 새지 않게 한다.
  sessionStorage.clear()
})
