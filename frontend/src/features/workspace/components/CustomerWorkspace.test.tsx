import {
  HttpResponse,
  http,
} from 'msw'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { CustomerWorkspace } from './CustomerWorkspace'

const accountsEndpoint =
  'http://localhost:5173/api/v1/accounts'
const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'
const notificationsEndpoint =
  'http://localhost:5173/api/v1/notifications'
const deadLettersEndpoint =
  'http://localhost:5173/api/v1/admin/outbox/dead-letters'
const settlementDiscrepanciesEndpoint =
  'http://localhost:5173/api/v1/settlement-discrepancies'
const auditEventsEndpoint =
  'http://localhost:5173/api/v1/audit-events'
const operationalSummaryEndpoint =
  'http://localhost:5173/api/v1/reports/operational-summary'

const firstAccount: CustomerAccount = {
  id:
    '11111111-1111-4111-8111-1111111177b9',
  customerId:
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  currency: 'GBP',
  balanceMinorUnits: 5000,
  status: 'ACTIVE',
  createdAt: '2026-07-01T09:00:00Z',
  updatedAt: '2026-07-01T09:00:00Z',
  version: 0,
}

const secondAccount: CustomerAccount = {
  ...firstAccount,
  id:
    '22222222-2222-4222-8222-22222222bc28',
  balanceMinorUnits: 2500,
}

beforeEach(() => {
  server.use(
    http.get(accountsEndpoint, () => {
      return HttpResponse.json([
        firstAccount,
        secondAccount,
      ])
    }),

    http.get(sessionEndpoint, () => {
      return HttpResponse.json({
        userId:
          'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        email: 'customer@example.com',
        roles: ['CUSTOMER'],
      })
    }),

    http.get(notificationsEndpoint, () => {
      return HttpResponse.json([])
    }),

    http.get(
      settlementDiscrepanciesEndpoint,
      () => {
        return HttpResponse.json({
          discrepancies: [],
          nextAfterCreatedAt: null,
          nextAfterId: null,
        })
      },
    ),

    http.get(auditEventsEndpoint, () => {
      return HttpResponse.json({
        events: [],
        nextCursor: null,
      })
    }),

    http.get(
      operationalSummaryEndpoint,
      () => {
        return HttpResponse.json({
          from: '2026-07-18T10:00:00Z',
          to: '2026-07-25T10:00:00Z',
          payment: null,
          settlement: null,
          reconciliation: null,
        })
      },
    ),
  )
})

describe('CustomerWorkspace', () => {
  it(
    'renders the authenticated workspace structure',
    async () => {
      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      expect(
        await screen.findByRole('heading', {
          level: 3,
          name: 'Manage simulated payments',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Your GBP accounts',
        }),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole('link', {
          name: 'Audit and reports',
        }),
      ).not.toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Payment notifications',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(
          'No notifications yet',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Create an internal payment',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByRole(
          'textbox',
          {
            name: 'Payment amount',
          },
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Find a payment',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('textbox', {
          name: 'Payment identifier',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('button', {
          name: 'Find payment',
        }),
      ).toBeEnabled()
    },
  )

  it(
    'provides in-page workspace navigation',
    async () => {
      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      expect(
        await screen.findByRole('link', {
          name: 'Accounts',
        }),
      ).toHaveAttribute(
        'href',
        '#customer-accounts',
      )

      expect(
        screen.getByRole('link', {
          name: 'Notifications',
        }),
      ).toHaveAttribute(
        'href',
        '#payment-notifications',
      )

      expect(
        screen.getByRole('link', {
          name: 'Create payment',
        }),
      ).toHaveAttribute(
        'href',
        '#create-payment',
      )

      expect(
        screen.getByRole('link', {
          name: 'Payment lookup',
        }),
      ).toHaveAttribute(
        'href',
        '#payment-lookup',
      )

      expect(
        screen.queryByRole('link', {
          name: 'Dead letters',
        }),
      ).not.toBeInTheDocument()

      expect(
        screen.getByText(
          'This workspace uses synthetic data and never moves real funds.',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows administrator dead-letter recovery only to administrators',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json({
            userId:
              'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            email: 'admin@example.com',
            roles: ['CUSTOMER', 'ADMIN'],
          })
        }),

        http.get(deadLettersEndpoint, () => {
          return HttpResponse.json([])
        }),
      )

      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      expect(
        await screen.findByRole('link', {
          name: 'Dead letters',
        }),
      ).toHaveAttribute(
        'href',
        '#outbox-dead-letters',
      )

      expect(
        screen.getByRole('link', {
          name: 'Audit and reports',
        }),
      ).toHaveAttribute(
        'href',
        '#audit-reporting',
      )

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Outbox dead-letter recovery',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(
          'No dead-letter events',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'keeps the exact payment amount in the payment form',
    async () => {
      const user = userEvent.setup()

      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      await user.type(
        await screen.findByRole(
          'textbox',
          {
            name: 'Payment amount',
          },
        ),
        '25.4',
      )

      expect(
        screen.getByText('£25.40'),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          '2540 minor units',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows reconciliation without customer payment calls for an analyst',
    async () => {
      server.use(
        http.get(sessionEndpoint, () => {
          return HttpResponse.json({
            userId:
              'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
            email: 'analyst@example.com',
            roles: [
              'RECONCILIATION_ANALYST',
            ],
          })
        }),
      )

      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      expect(
        await screen.findByRole('heading', {
          level: 3,
          name:
            'Review settlement operations',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByRole('link', {
          name: 'Settlement import',
        }),
      ).toHaveAttribute(
        'href',
        '#settlement-import',
      )

      expect(
        screen.getByRole('heading', {
          level: 4,
          name:
            'Import and reconcile CSV',
        }),
      ).toBeInTheDocument()

      expect(
        screen.queryByRole('heading', {
          level: 4,
          name: 'Your GBP accounts',
        }),
      ).not.toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Audit event search',
        }),
      ).toBeInTheDocument()
    },
  )
})
