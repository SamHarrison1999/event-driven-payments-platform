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

import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import { PaymentCreationForm } from './PaymentCreationForm'

const endpoint =
  'http://localhost:5173/api/v1/accounts'

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
    http.get(endpoint, () => {
      return HttpResponse.json(accounts)
    }),
  )
}

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
})
