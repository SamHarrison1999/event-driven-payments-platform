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

import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { CustomerWorkspace } from './CustomerWorkspace'

const accountsEndpoint =
  'http://localhost:5173/api/v1/accounts'

beforeEach(() => {
  server.use(
    http.get(accountsEndpoint, () => {
      return HttpResponse.json([])
    }),
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
        screen.getByRole('heading', {
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
        screen.getByRole('heading', {
          level: 4,
          name: 'Create an internal payment',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('textbox', {
          name: 'Payment amount',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 4,
          name: 'Find a payment',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(
          'No accounts available',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'provides in-page workspace navigation',
    () => {
      renderWithQueryClient(
        <CustomerWorkspace />,
      )

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
        screen.getByText(
          'This workspace uses synthetic data and never moves real funds.',
        ),
      ).toBeInTheDocument()
    },
  )

  it(
    'keeps the exact payment amount in workspace state',
    async () => {
      const user = userEvent.setup()

      renderWithQueryClient(
        <CustomerWorkspace />,
      )

      await user.type(
        screen.getByRole('textbox', {
          name: 'Payment amount',
        }),
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
})
