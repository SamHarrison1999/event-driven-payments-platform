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

import { clearCsrfToken } from '../../../shared/api/csrfToken'
import {
  customerSessionStorageKeys,
} from '../../../shared/storage/customerSessionStorage'
import { server } from '../../../test/server'
import type { PaymentDraft } from '../model/paymentDraft'
import {
  isRetryablePaymentSubmissionError,
  submitPaymentIdempotently,
} from './submitPaymentIdempotently'
import {
  ApiProblemError,
} from '../../../shared/api/apiProblem'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const paymentEndpoint =
  'http://localhost:5173/api/v1/payments'

const userId =
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'

const draft: PaymentDraft = {
  sourceAccountId:
    '11111111-1111-4111-8111-111111111111',
  destinationAccountId:
    '22222222-2222-4222-8222-222222222222',
  amountMinorUnits: 1050,
}

function useCsrfHandler() {
  server.use(
    http.get(csrfEndpoint, () => {
      return HttpResponse.json({
        headerName: 'X-CSRF-TOKEN',
        parameterName: '_csrf',
        token: 'payment-csrf-token',
      })
    }),
  )
}

beforeEach(() => {
  clearCsrfToken()
  window.sessionStorage.clear()
})

describe(
  'submitPaymentIdempotently',
  () => {
    it(
      'reuses the same key after an uncertain network failure',
      async () => {
        useCsrfHandler()

        const receivedKeys: string[] = []
        let attempts = 0

        server.use(
          http.post(
            paymentEndpoint,
            ({ request }) => {
              receivedKeys.push(
                request.headers.get(
                  'Idempotency-Key',
                ) ?? '',
              )
              attempts += 1

              if (attempts === 1) {
                return HttpResponse.error()
              }

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
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).rejects.toThrow()

        await expect(
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).resolves.toMatchObject({
          status: 'COMPLETED',
        })

        expect(receivedKeys).toHaveLength(2)
        expect(receivedKeys[0]).toBe(
          receivedKeys[1],
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).toBeNull()
      },
    )

    it(
      'retains the key while the backend reports processing',
      async () => {
        useCsrfHandler()

        server.use(
          http.post(paymentEndpoint, () => {
            return HttpResponse.json(
              {
                type:
                  'urn:problem:payment:idempotency-request-in-progress',
                title:
                  'Payment request in progress',
                status: 409,
                detail:
                  'A payment request with this idempotency key is still processing.',
                code:
                  'IDEMPOTENCY_REQUEST_IN_PROGRESS',
              },
              {
                status: 409,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            )
          }),
        )

        await expect(
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).rejects.toBeInstanceOf(
          ApiProblemError,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).not.toBeNull()
      },
    )

    it(
      'retains the key for an unclassified problem outcome',
      async () => {
        useCsrfHandler()

        server.use(
          http.post(paymentEndpoint, () => {
            return HttpResponse.json(
              {
                type:
                  'urn:problem:payment:unexpected-processing-error',
                title:
                  'Unexpected processing error',
                status: 503,
                detail:
                  'The final payment outcome could not be confirmed.',
                code:
                  'PAYMENT_OUTCOME_UNCONFIRMED',
              },
              {
                status: 503,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            )
          }),
        )

        await expect(
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).rejects.toBeInstanceOf(
          ApiProblemError,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).not.toBeNull()
      },
    )
    it(
      'retains the key when authentication expires',
      async () => {
        useCsrfHandler()

        server.use(
          http.post(paymentEndpoint, () => {
            return HttpResponse.json(
              {
                type:
                  'urn:problem:security:authentication-required',
                title:
                  'Authentication required',
                status: 401,
                detail:
                  'Authentication is required to submit a payment.',
                code:
                  'SECURITY_AUTHENTICATION_REQUIRED',
              },
              {
                status: 401,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            )
          }),
        )

        await expect(
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).rejects.toBeInstanceOf(
          ApiProblemError,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).not.toBeNull()
      },
    )

    it(
      'clears the key after a terminal problem response',
      async () => {
        useCsrfHandler()

        server.use(
          http.post(paymentEndpoint, () => {
            return HttpResponse.json(
              {
                type:
                  'urn:problem:payment:insufficient-funds',
                title: 'Payment rejected',
                status: 422,
                detail:
                  'The source account has insufficient funds.',
                code:
                  'PAYMENT_INSUFFICIENT_FUNDS',
              },
              {
                status: 422,
                headers: {
                  'Content-Type':
                    'application/problem+json',
                },
              },
            )
          }),
        )

        await expect(
          submitPaymentIdempotently(
            userId,
            draft,
          ),
        ).rejects.toBeInstanceOf(
          ApiProblemError,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).toBeNull()
      },
    )
  },
)

describe(
  'isRetryablePaymentSubmissionError',
  () => {
    it(
      'retries unknown outcomes and processing conflicts only',
      () => {
        expect(
          isRetryablePaymentSubmissionError(
            new Error('network'),
          ),
        ).toBe(true)

        expect(
          isRetryablePaymentSubmissionError(
            new ApiProblemError({
              title:
                'Payment request in progress',
              status: 409,
              detail: 'Still processing.',
              code:
                'IDEMPOTENCY_REQUEST_IN_PROGRESS',
            }),
          ),
        ).toBe(true)

        expect(
          isRetryablePaymentSubmissionError(
            new ApiProblemError({
              title:
                'Unexpected processing error',
              status: 503,
              detail:
                'The final outcome is unknown.',
              code:
                'PAYMENT_OUTCOME_UNCONFIRMED',
            }),
          ),
        ).toBe(true)

        expect(
          isRetryablePaymentSubmissionError(
            new ApiProblemError({
              title: 'Payment rejected',
              status: 422,
              detail:
                'Insufficient funds.',
              code:
                'PAYMENT_INSUFFICIENT_FUNDS',
            }),
          ),
        ).toBe(false)
      },
    )
  },
)
