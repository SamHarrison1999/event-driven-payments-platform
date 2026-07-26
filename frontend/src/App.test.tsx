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

import App from './App'
import type { IdentitySession } from './features/identity/api/identitySession'
import type { SystemInfo } from './features/system/api/getSystemInfo'
import { renderWithQueryClient } from './test/renderWithQueryClient'
import { server } from './test/server'

const systemEndpoint =
  'http://localhost:5173/api/v1/system/info'

const sessionEndpoint =
  'http://localhost:5173/api/v1/identity/session'

const systemInfo: SystemInfo = {
  name: 'Event-Driven Payments and Reconciliation Platform',
  description:
    'Educational simulation of payment processing and settlement reconciliation',
  version: '0.0.1-SNAPSHOT',
  educational: true,
  realMoneyProcessing: false,
}

const session: IdentitySession = {
  userId:
    '2f1f55da-5793-4a75-aeb5-c20f69f16949',
  email: 'sam.customer@example.com',
  roles: ['CUSTOMER'],
}

beforeEach(() => {
  server.use(
    http.get(sessionEndpoint, () => {
      return HttpResponse.json(session)
    }),

    http.get(
      'http://localhost:5173/api/v1/accounts',
      () => {
        return HttpResponse.json([])
      },
    ),

    http.get(
      'http://localhost:5173/api/v1/notifications',
      () => {
        return HttpResponse.json([])
      },
    ),
  )
})

describe('App', () => {
  it(
    'renders the platform shell, customer workspace and system information',
    async () => {
      server.use(
        http.get(systemEndpoint, () => {
          return HttpResponse.json(systemInfo)
        }),
      )

      renderWithQueryClient(<App />)

      expect(
        screen.getByRole('heading', {
          level: 1,
          name: 'Payments operations workspace',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          'Audit and operational reporting',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Phase 10'),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(
          session.email,
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 3,
          name: 'Manage simulated payments',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('link', {
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
        await screen.findByText(systemInfo.name),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Connected'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Educational only'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Disabled'),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows an accessible error when the backend is unavailable',
    async () => {
      server.use(
        http.get(systemEndpoint, () => {
          return new HttpResponse(null, {
            status: 503,
          })
        }),
      )

      renderWithQueryClient(<App />)

      const alert = await screen.findByRole(
        'alert',
      )

      expect(alert).toHaveTextContent(
        'Backend unavailable',
      )

      expect(
        screen.getByRole('button', {
          name: 'Try again',
        }),
      ).toBeEnabled()
    },
  )

  it(
    'allows the user to retry a failed system request',
    async () => {
      let attempts = 0

      server.use(
        http.get(systemEndpoint, () => {
          attempts += 1

          if (attempts === 1) {
            return new HttpResponse(null, {
              status: 503,
            })
          }

          return HttpResponse.json(systemInfo)
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(<App />)

      const retryButton =
        await screen.findByRole('button', {
          name: 'Try again',
        })

      await user.click(retryButton)

      expect(
        await screen.findByText(systemInfo.name),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )
})
