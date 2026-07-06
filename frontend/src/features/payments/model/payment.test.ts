import {
  describe,
  expect,
  it,
} from 'vitest'

import { isPaymentDetails } from './payment'

const basePayment = {
  paymentId:
    '33333333-3333-4333-8333-333333333333',
  sourceAccountId:
    '11111111-1111-4111-8111-111111111111',
  destinationAccountId:
    '22222222-2222-4222-8222-222222222222',
  amountMinorUnits: 1050,
  currency: 'GBP',
  createdAt: '2026-07-01T10:15:00Z',
  updatedAt: '2026-07-01T10:16:00Z',
  version: 2,
}

describe('isPaymentDetails', () => {
  it(
    'accepts a completed payment with a ledger transaction',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'COMPLETED',
          ledgerTransactionId:
            '44444444-4444-4444-8444-444444444444',
        }),
      ).toBe(true)
    },
  )

  it(
    'accepts pending and processing payments without terminal metadata',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'PENDING',
        }),
      ).toBe(true)

      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'PROCESSING',
        }),
      ).toBe(true)
    },
  )

  it(
    'accepts a rejected payment with a known rejection reason',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'REJECTED',
          rejectionReason:
            'PAYMENT_INSUFFICIENT_FUNDS',
        }),
      ).toBe(true)
    },
  )

  it(
    'accepts a failed payment with a known failure reason',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'FAILED',
          failureReason:
            'PAYMENT_CONCURRENT_MODIFICATION',
        }),
      ).toBe(true)
    },
  )

  it(
    'rejects lifecycle metadata that does not match the status',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'COMPLETED',
          rejectionReason:
            'PAYMENT_INSUFFICIENT_FUNDS',
        }),
      ).toBe(false)

      expect(
        isPaymentDetails({
          ...basePayment,
          status: 'REJECTED',
          rejectionReason:
            'PAYMENT_UNKNOWN_REASON',
        }),
      ).toBe(false)
    },
  )

  it(
    'rejects invalid identifiers, amounts and timestamps',
    () => {
      expect(
        isPaymentDetails({
          ...basePayment,
          paymentId: 'not-a-uuid',
          status: 'PROCESSING',
        }),
      ).toBe(false)

      expect(
        isPaymentDetails({
          ...basePayment,
          amountMinorUnits: 0,
          status: 'PROCESSING',
        }),
      ).toBe(false)

      expect(
        isPaymentDetails({
          ...basePayment,
          updatedAt: '2026-06-30T10:16:00Z',
          status: 'PROCESSING',
        }),
      ).toBe(false)
    },
  )
})
