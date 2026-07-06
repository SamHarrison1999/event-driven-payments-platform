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

import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { PaymentCreationForm } from './PaymentCreationForm'

const accountEndpoint =
  'http://localhost:5173/api/v1/accounts'
const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'
const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const paymentEndpoint =
  'http://localhost:5173/api/v1/payments'

const identitySession = {
  userId:
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  email: 'customer@example.com',
  roles: ['CUSTOMER'],
}

const sourceAccount: CustomerAccount = {
  id:
    '11111111-1111-4111-8111-1111111177b9',
  customerId:
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  currency: 'GBP',
  balanceMinorUnits: 1250,
  status: 'ACTIVE',
  createdAt: '2026-07-01T09:00:00Z',
  updatedAt: '2026-07-01T09:00:00Z',
  version: 0,
}

const destinationAccount: CustomerAccount = {
  ...sourceAccount,
  id:
    '22222222-2222-4222-8222-22222222bc28',
  balanceMinorUnits: 5000,
}

const frozenAccount: CustomerAccount = {
  ...sourceAccount,
  id:
    '33333333-3333-4333-8333-33333333f001',
  status: 'FROZEN',
}

function useAccounts(
  accounts: CustomerAccount[],
) {
  server.use(
    http.get(accountEndpoint, () => {
      return HttpResponse.json(accounts)
    }),

    http.get(sessionEndpoint, () => {
      return HttpResponse.json(
        identitySession,
      )
    }),
  )
}

async function prepareReview(
  user: ReturnType<
    typeof userEvent.setup
  >,
) {
  await user.selectOptions(
    await screen.findByRole(
      'combobox',
      {
        name: 'Source account',
      },
    ),
    sourceAccount.id,
  )

  await user.selectOptions(
    screen.getByRole('combobox', {
      name: 'Destination account',
    }),
    destinationAccount.id,
  )

  await user.type(
    screen.getByRole('textbox', {
      name: 'Payment amount',
    }),
    '10.5',
  )

  await user.click(
    screen.getByRole('button', {
      name: 'Review payment',
    }),
  )
}

beforeEach(() => {
  clearCsrfToken()
  window.sessionStorage.clear()
})

describe('PaymentCreationForm', () => {
  it(
    'offers only active accounts for payment routing',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
        frozenAccount,
      ])

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      const sourceSelect =
        await screen.findByRole(
          'combobox',
          {
            name: 'Source account',
          },
        )

      expect(sourceSelect).toHaveTextContent(
        'Account ending 77B9 — £12.50',
      )

      expect(sourceSelect).toHaveTextContent(
        'Account ending BC28 — £50.00',
      )

      expect(sourceSelect).not.toHaveTextContent(
        'F001',
      )
    },
  )

  it(
    'builds a reviewable exact payment draft without submitting it',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Review payment draft',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Account ending 77B9',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Account ending BC28',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('£10.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'The payment has not been submitted',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'preserves reviewed fields when the customer edits the draft',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      await user.click(
        screen.getByRole('button', {
          name: 'Edit payment details',
        }),
      )

      expect(
        screen.getByRole('combobox', {
          name: 'Source account',
        }),
      ).toHaveValue(sourceAccount.id)

      expect(
        screen.getByRole('combobox', {
          name: 'Destination account',
        }),
      ).toHaveValue(
        destinationAccount.id,
      )

      expect(
        screen.getByRole('textbox', {
          name: 'Payment amount',
        }),
      ).toHaveValue('10.5')
    },
  )
  it(
    'requires complete details before review',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: 'Review payment',
          },
        ),
      )

      expect(
        screen.getByText(
          'Review the payment details',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Choose a source account.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Choose a destination account.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Enter a payment amount.',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'rejects an amount above the selected source balance',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await user.selectOptions(
        await screen.findByRole(
          'combobox',
          {
            name: 'Source account',
          },
        ),
        sourceAccount.id,
      )

      await user.selectOptions(
        screen.getByRole('combobox', {
          name: 'Destination account',
        }),
        destinationAccount.id,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment amount',
        }),
        '12.51',
      )

      await user.click(
        screen.getByRole('button', {
          name: 'Review payment',
        }),
      )

      expect(
        screen.getByText(
          'The payment amount exceeds the source account balance.',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'explains when there are not enough active accounts',
    async () => {
      useAccounts([
        sourceAccount,
        frozenAccount,
      ])

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      expect(
        await screen.findByText(
          'Two active accounts are required',
        ),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole('form'),
      ).not.toBeInTheDocument()
    },
  )

  it(
    'submits the exact draft with CSRF and idempotency protection',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

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

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      await user.click(
        screen.getByRole('button', {
          name: 'Submit payment',
        }),
      )

      expect(
        await screen.findByText(
          'Payment completed',
        ),
      ).toBeInTheDocument()

      expect(receivedBody).toEqual({
        sourceAccountId:
          sourceAccount.id,
        destinationAccountId:
          destinationAccount.id,
        amountMinorUnits: 1050,
      })

      expect(receivedCsrf).toBe(
        'payment-csrf-token',
      )

      expect(receivedKey).toMatch(
        /^[0-9a-f-]{36}$/i,
      )
    },
  )

  it(
    'reuses the same key when an uncertain result is retried',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      const receivedKeys: string[] = []
      let attempts = 0

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

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      await user.click(
        screen.getByRole('button', {
          name: 'Submit payment',
        }),
      )

      expect(
        await screen.findByText(
          'Payment result not confirmed',
        ),
      ).toBeInTheDocument()

      await user.click(
        screen.getByRole('button', {
          name: 'Retry payment',
        }),
      )

      expect(
        await screen.findByText(
          'Payment completed',
        ),
      ).toBeInTheDocument()

      expect(receivedKeys).toHaveLength(2)
      expect(receivedKeys[0]).toBe(
        receivedKeys[1],
      )
    },
  )

  it(
    'shows terminal backend rejection without offering an unsafe retry',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

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

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      await user.click(
        screen.getByRole('button', {
          name: 'Submit payment',
        }),
      )

      expect(
        await screen.findByText(
          'Payment rejected',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'The source account has insufficient funds.',
        ),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole('button', {
          name: 'Retry payment',
        }),
      ).not.toBeInTheDocument()
    },
  )

  it(
    'invalidates account data after completion',
    async () => {
      useAccounts([
        sourceAccount,
        destinationAccount,
      ])

      let accountRequests = 0

      server.use(
        http.get(accountEndpoint, () => {
          accountRequests += 1

          return HttpResponse.json([
            sourceAccount,
            destinationAccount,
          ])
        }),

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
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <PaymentCreationForm />,
      )

      await prepareReview(user)

      await user.click(
        screen.getByRole('button', {
          name: 'Submit payment',
        }),
      )

      await screen.findByText(
        'Payment completed',
      )

      await waitFor(() => {
        expect(accountRequests).toBe(2)
      })
    },
  )
})
