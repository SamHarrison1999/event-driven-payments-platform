import {
  HttpResponse,
  http,
} from 'msw'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { PaymentLookup } from './PaymentLookup'

const paymentId =
  '33333333-3333-4333-8333-333333333333'

const endpoint =
  `http://localhost:5173/api/v1/payments/${paymentId}`

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

      expect(
        await screen.findByRole(
          'heading',
          {
            level: 5,
            name: 'Payment processing',
          },
        ),
      ).toBeInTheDocument()

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

      expect(
        await screen.findByRole('alert'),
      ).toHaveTextContent(
        'No customer-owned payment is available for this identifier.',
      )
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
