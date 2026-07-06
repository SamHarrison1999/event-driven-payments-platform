import {
  render,
  screen,
} from '@testing-library/react'
import {
  describe,
  expect,
  it,
} from 'vitest'

import type { PaymentReceiptData } from '../model/payment'
import { PaymentReceipt } from './PaymentReceipt'

const basePayment: PaymentReceiptData = {
  paymentId:
    '33333333-3333-4333-8333-333333333333',
  sourceAccountId:
    '11111111-1111-4111-8111-1111111177b9',
  destinationAccountId:
    '22222222-2222-4222-8222-22222222bc28',
  amountMinorUnits: 1050,
  currency: 'GBP',
  status: 'PROCESSING',
}

describe('PaymentReceipt', () => {
  it(
    'displays an exact completed payment receipt',
    () => {
      render(
        <PaymentReceipt
          payment={{
            ...basePayment,
            status: 'COMPLETED',
            ledgerTransactionId:
              '44444444-4444-4444-8444-444444444444',
            createdAt:
              '2026-07-01T10:15:00Z',
            updatedAt:
              '2026-07-01T10:16:00Z',
            version: 2,
          }}
        />,
      )

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Payment completed',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText('£10.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Account ending 77B9',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          '44444444-4444-4444-8444-444444444444',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('2'),
      ).toBeInTheDocument()
    },
  )

  it(
    'explains a rejected payment reason',
    () => {
      render(
        <PaymentReceipt
          payment={{
            ...basePayment,
            status: 'REJECTED',
            rejectionReason:
              'PAYMENT_INSUFFICIENT_FUNDS',
          }}
        />,
      )

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Payment rejected',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Source account has insufficient funds',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'marks an in-progress payment as non-terminal',
    () => {
      render(
        <PaymentReceipt
          payment={basePayment}
        />,
      )

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Payment processing',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          /Look it up again for the latest result/,
        ),
      ).toBeInTheDocument()
    },
  )
})
