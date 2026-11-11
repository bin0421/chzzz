import { loadTossPayments } from '@tosspayments/tosspayments-sdk'
import type { PaymentAdapter, WidgetMountRequest } from './PaymentAdapter'

/**
 * 토스페이먼츠 결제위젯(주문서형, 테스트 모드). 페이지 안에 결제수단 목록과 약관 동의를 그리고,
 * 결제하기를 누르면 고른 결제수단의 테스트 결제 화면으로 이어진다(ADR-0007).
 */
export const tossPaymentAdapter: PaymentAdapter = {
  async mountWidget(request: WidgetMountRequest) {
    const tossPayments = await loadTossPayments(request.clientKey)
    const widgets = tossPayments.widgets({ customerKey: request.customerKey })
    await widgets.setAmount({ currency: 'KRW', value: request.amount })
    const [methods, agreement] = await Promise.all([
      widgets.renderPaymentMethods({ selector: request.methodsSelector, variantKey: 'DEFAULT' }),
      widgets.renderAgreement({ selector: request.agreementSelector, variantKey: 'AGREEMENT' }),
    ])
    return {
      async requestPayment(payment) {
        await widgets.requestPayment(payment)
      },
      destroy() {
        void methods.destroy()
        void agreement.destroy()
      },
    }
  },
}
