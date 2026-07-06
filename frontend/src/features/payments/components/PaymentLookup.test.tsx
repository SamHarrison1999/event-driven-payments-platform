import {
  HttpResponse,
  http,
} from 'msw'
import {
  screen,
  waitFor,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { SessionBoundary } from '../../identity/components/SessionBoundary'
import {
  customerSessionStorageKeys,
} from '../../../shared/storage/customerSessionStorage'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { PaymentLookup } from './PaymentLookup'

const paymentId =
  '33333333-3333-4333-8333-333333333333'

const endpoint =
  `http://localhost:5173/api/v1/payments/${paymentId}`

const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'

const processingPayment = {
  paymentId,
  sourceAccountId:
    '11111111-1111-4111-8111-1111111177b9',
  destinationAccountId:
    '22222222-2222-4222-8222-22222222bc28',
  amountMinorUnits: 2540,
  currency: 'GBP',
  status: 'PROCESSING',
  createdAt: '2026-07-01T10:15:00Z',
  updatedAt: '2026-07-01T10:16:00Z',
  version: 1,
}

beforeEach(() => {
  window.sessionStorage.clear()
})

describe('PaymentLookup', () => {
  it(
    'validates the UUID before making a request',
    async () => {
      let requests = 0

      server.use(
        http.get(
          'http://localhost:5173/api/v1/payments/:paymentId',
          () => {
            requests += 1
            return HttpResponse.json(
              processingPayment,
            )
          },
        ),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentLookup />,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        }),
        'not-a-uuid',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      )

      expect(
        screen.getByText(
          'Enter a valid payment UUID.',
        ),
      ).toBeInTheDocument()

      expect(requests).toBe(0)
    },
  )

  it(
    'marks the lookup region busy while one request is pending',
    async () => {
      let requests = 0
      let releaseResponse:
        (() => void) | undefined

      const responseGate =
        new Promise<void>((resolve) => {
          releaseResponse = resolve
        })

      server.use(
        http.get(endpoint, async () => {
          requests += 1
          await responseGate

          return HttpResponse.json(
            processingPayment,
          )
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentLookup />,
      )

      const input =
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        })

      await user.type(input, paymentId)

      const findButton =
        screen.getByRole('button', {
          name: 'Find payment',
        })

      await user.click(findButton)

      await waitFor(() => {
        expect(requests).toBe(1)
      })

      expect(findButton).toBeDisabled()

      expect(
        input.closest('section'),
      ).toHaveAttribute(
        'aria-busy',
        'true',
      )

      releaseResponse?.()

      expect(
        await screen.findByRole(
          'heading',
          {
            level: 5,
            name: 'Payment processing',
          },
        ),
      ).toBeInTheDocument()
    },
  )
  it(
    'retrieves and displays an in-progress payment',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(
            processingPayment,
          )
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentLookup />,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        }),
        `  ${paymentId}  `,
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      )

      const heading =
        await screen.findByRole(
          'heading',
          {
            level: 5,
            name: 'Payment processing',
          },
        )

      expect(heading).toBeInTheDocument()
      expect(heading).toHaveFocus()

      expect(
        screen.getByText('£25.40'),
      ).toBeInTheDocument()

      expect(
        screen.getByText(paymentId),
      ).toBeInTheDocument()
    },
  )

  it(
    'uses one privacy-preserving message for an unavailable payment',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(
            {
              type:
                'urn:problem:payment:not-found',
              title: 'Payment not found',
              status: 404,
              detail:
                'The requested payment was not found.',
              code: 'PAYMENT_NOT_FOUND',
            },
            {
              status: 404,
              headers: {
                'Content-Type':
                  'application/problem+json',
              },
            },
          )
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentLookup />,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        }),
        paymentId,
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      )

      const alert =
        await screen.findByRole('alert')

      expect(alert).toHaveTextContent(
        'No customer-owned payment is available for this identifier.',
      )

      expect(alert).toHaveFocus()
    },
  )

  it(
    'returns to sign in when the lookup session expires without clearing retry state',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json({
            userId:
              'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            email: 'customer@example.com',
            roles: ['CUSTOMER'],
          })
        }),

        http.get(endpoint, () => {
          return HttpResponse.json(
            {
              type:
                'urn:problem:security:authentication-required',
              title:
                'Authentication required',
              status: 401,
              detail:
                'Authentication is required to retrieve a payment.',
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

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <SessionBoundary>
          <PaymentLookup />
        </SessionBoundary>,
      )

      await user.type(
        await screen.findByRole(
          'textbox',
          {
            name: 'Payment identifier',
          },
        ),
        paymentId,
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      )

      expect(
        await screen.findByRole('heading', {
          level: 3,
          name: 'Sign in',
        }),
      ).toBeInTheDocument()

      expect(
        window.sessionStorage.getItem(
          customerSessionStorageKeys
            .paymentSubmission,
        ),
      ).toBe('unresolved-payment')
    },
  )
  it(
    'shows a recoverable message for a network failure',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.error()
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentLookup />,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        }),
        paymentId,
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      )

      expect(
        await screen.findByRole('alert'),
      ).toHaveTextContent(
        'Payment lookup unavailable',
      )
    },
  )
})
