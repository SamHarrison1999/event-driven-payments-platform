import {
  HttpResponse,
  http,
} from 'msw'
import {
  describe,
  expect,
  it,
} from 'vitest'

import {
  ApiContractError,
} from '../../../shared/api/apiClient'
import { server } from '../../../test/server'
import { getPayment } from './getPayment'

const paymentId =
  '33333333-3333-4333-8333-333333333333'

const endpoint =
  `http://localhost:5173/api/v1/payments/${paymentId}`

describe('getPayment', () => {
  it(
    'retrieves and validates a customer-owned payment',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json({
            paymentId,
            sourceAccountId:
              '11111111-1111-4111-8111-111111111111',
            destinationAccountId:
              '22222222-2222-4222-8222-222222222222',
            amountMinorUnits: 1050,
            currency: 'GBP',
            status: 'COMPLETED',
            ledgerTransactionId:
              '44444444-4444-4444-8444-444444444444',
            rejectionReason: null,
            failureReason: null,
            createdAt:
              '2026-07-01T10:15:00Z',
            updatedAt:
              '2026-07-01T10:16:00Z',
            version: 2,
          })
        }),
      )

      await expect(
        getPayment(paymentId),
      ).resolves.toMatchObject({
        paymentId,
        status: 'COMPLETED',
        amountMinorUnits: 1050,
      })
    },
  )

  it(
    'rejects a response with inconsistent lifecycle metadata',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json({
            paymentId,
            sourceAccountId:
              '11111111-1111-4111-8111-111111111111',
            destinationAccountId:
              '22222222-2222-4222-8222-222222222222',
            amountMinorUnits: 1050,
            currency: 'GBP',
            status: 'REJECTED',
            ledgerTransactionId:
              '44444444-4444-4444-8444-444444444444',
            createdAt:
              '2026-07-01T10:15:00Z',
            updatedAt:
              '2026-07-01T10:16:00Z',
            version: 2,
          })
        }),
      )

      await expect(
        getPayment(paymentId),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
