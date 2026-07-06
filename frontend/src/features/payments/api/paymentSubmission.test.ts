import {
  HttpResponse,
  http,
} from 'msw'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import {
  ApiContractError,
} from '../../../shared/api/apiClient'
import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { server } from '../../../test/server'
import { submitPayment } from './paymentSubmission'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const paymentEndpoint =
  'http://localhost:5173/api/v1/payments'

const draft = {
  sourceAccountId:
    '11111111-1111-4111-8111-111111111111',
  destinationAccountId:
    '22222222-2222-4222-8222-222222222222',
  amountMinorUnits: 1050,
}

beforeEach(() => {
  clearCsrfToken()
})

describe('submitPayment', () => {
  it(
    'submits the exact draft with CSRF and idempotency headers',
    async () => {
      let receivedBody: unknown
      let receivedCsrf: string | null = null
      let receivedKey: string | null = null

      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'payment-csrf-token',
          })
        }),

        http.post(
          paymentEndpoint,
          async ({ request }) => {
            receivedBody =
              await request.json()
            receivedCsrf =
              request.headers.get(
                'X-CSRF-TOKEN',
              )
            receivedKey =
              request.headers.get(
                'Idempotency-Key',
              )

            return HttpResponse.json(
              {
                paymentId:
                  '33333333-3333-4333-8333-333333333333',
                status: 'COMPLETED',
                ledgerTransactionId:
                  '44444444-4444-4444-8444-444444444444',
              },
              {
                status: 201,
              },
            )
          },
        ),
      )

      await expect(
        submitPayment(
          draft,
          'payment-key-123',
        ),
      ).resolves.toEqual({
        paymentId:
          '33333333-3333-4333-8333-333333333333',
        status: 'COMPLETED',
        ledgerTransactionId:
          '44444444-4444-4444-8444-444444444444',
      })

      expect(receivedBody).toEqual(draft)
      expect(receivedCsrf).toBe(
        'payment-csrf-token',
      )
      expect(receivedKey).toBe(
        'payment-key-123',
      )
    },
  )

  it(
    'requires HTTP status 201 for a completed submission',
    async () => {
      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'payment-csrf-token',
          })
        }),

        http.post(paymentEndpoint, () => {
          return HttpResponse.json({
            paymentId:
              '33333333-3333-4333-8333-333333333333',
            status: 'COMPLETED',
            ledgerTransactionId:
              '44444444-4444-4444-8444-444444444444',
          })
        }),
      )

      await expect(
        submitPayment(
          draft,
          'payment-key-123',
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
  it(
    'rejects an invalid successful response contract',
    async () => {
      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'payment-csrf-token',
          })
        }),

        http.post(paymentEndpoint, () => {
          return HttpResponse.json(
            {
              paymentId: 'not-a-uuid',
              status: 'COMPLETED',
            },
            {
              status: 201,
            },
          )
        }),
      )

      await expect(
        submitPayment(
          draft,
          'payment-key-123',
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
