import { vi } from 'vitest'
import type { PaymentAdapter, PaymentWidget, WidgetMountRequest, WidgetPaymentRequest } from '../payment/PaymentAdapter'

/** 결제위젯을 그리지 않고 호출만 기록하는 가짜 결제 어댑터. */
export function createFakePaymentAdapter() {
  const requestPayment = vi.fn(async (_request: WidgetPaymentRequest) => {})
  const destroy = vi.fn()
  const widget: PaymentWidget = { requestPayment, destroy }
  const mountWidget = vi.fn(async (_request: WidgetMountRequest) => widget)
  const adapter: PaymentAdapter = { mountWidget }
  return { adapter, mountWidget, requestPayment, destroy }
}
