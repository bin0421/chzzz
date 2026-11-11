import { createContext, useContext } from 'react'

/** 결제위젯을 그릴 때 넘기는 값. 금액은 서버가 후원을 만든 뒤 돌려준 값만 쓴다(ADR-0007). */
export type WidgetMountRequest = {
  clientKey: string
  customerKey: string
  amount: number
  /** 결제수단 목록과 약관 동의를 그릴 요소의 선택자. 요소가 화면에 있어야 한다. */
  methodsSelector: string
  agreementSelector: string
}

/** 위젯에서 결제를 요청할 때 넘기는 값. 주문번호는 서버가 정한 값만 쓴다. */
export type WidgetPaymentRequest = {
  orderId: string
  orderName: string
  successUrl: string
  failUrl: string
}

export interface PaymentWidget {
  /** 고른 결제수단으로 결제를 요청한다. 카드사 테스트 결제 화면으로 이어지고, 결과는 성공·실패 주소로 돌아온다. */
  requestPayment(request: WidgetPaymentRequest): Promise<void>
  destroy(): void
}

/**
 * 결제위젯 경계. 실제 구현은 토스 SDK로 페이지 안에 결제수단 목록·약관 동의를 그리고, 테스트는 가짜로 바꿔 끼운다.
 * 결제 결과는 성공·실패 주소로 돌아온 화면이 서버에 확인한다.
 */
export interface PaymentAdapter {
  mountWidget(request: WidgetMountRequest): Promise<PaymentWidget>
}

export const PaymentAdapterContext = createContext<PaymentAdapter | null>(null)

export function usePaymentAdapter(): PaymentAdapter {
  const adapter = useContext(PaymentAdapterContext)
  if (!adapter) throw new Error('PaymentAdapterContext가 주입되지 않았습니다.')
  return adapter
}
