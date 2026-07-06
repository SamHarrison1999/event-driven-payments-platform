import {
  HttpResponse,
  http,
} from 'msw'
import {
  screen,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type { CustomerAccount } from '../api/customerAccount'
import { CustomerAccountsPanel } from './CustomerAccountsPanel'

const endpoint =
  'http://localhost:5173/api/v1/accounts'

const activeAccount: CustomerAccount = {
  id:
    '4af96ca9-5012-4c4a-b52e-e052d3e977b9',
  customerId:
    'f56ff408-f9b6-4a7b-a319-b56907fa8679',
  currency: 'GBP',
  balanceMinorUnits: 1250,
  status: 'ACTIVE',
  createdAt: '2026-06-29T09:00:00Z',
  updatedAt: '2026-06-29T09:30:00Z',
  version: 2,
}

const frozenAccount: CustomerAccount = {
  ...activeAccount,
  id:
    '2cc61ee3-81dc-4c81-bf4e-baa82b68bc28',
  balanceMinorUnits: 5000,
  status: 'FROZEN',
  updatedAt: '2026-06-29T10:00:00Z',
}

describe('CustomerAccountsPanel', () => {
  it(
    'displays owned account balances and statuses',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            activeAccount,
            frozenAccount,
          ])
        }),
      )

      renderWithQueryClient(
        <CustomerAccountsPanel />,
      )

      expect(
        await screen.findByRole('list', {
          name: 'Owned GBP accounts',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Account ending 77B9',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Account ending BC28',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText('£12.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('£50.00'),
      ).toBeInTheDocument()

      expect(
        screen.getByLabelText(
          'Status: Active',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByLabelText(
          'Status: Frozen',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('2 accounts'),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows an empty state for a customer without accounts',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([])
        }),
      )

      renderWithQueryClient(
        <CustomerAccountsPanel />,
      )

      expect(
        await screen.findByText(
          'No accounts available',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('0 accounts'),
      ).toBeInTheDocument()
    },
  )

  it(
    'allows an unavailable account request to be retried',
    async () => {
      let attempts = 0

      server.use(
        http.get(endpoint, () => {
          attempts += 1

          if (attempts === 1) {
            return new HttpResponse(null, {
              status: 503,
            })
          }

          return HttpResponse.json([
            activeAccount,
          ])
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <CustomerAccountsPanel />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: 'Try again',
          },
        ),
      )

      expect(
        await screen.findByText('£12.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('1 account'),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )
})
