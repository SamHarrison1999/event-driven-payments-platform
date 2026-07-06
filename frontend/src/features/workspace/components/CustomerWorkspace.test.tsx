import {
  render,
  screen,
} from '@testing-library/react'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { CustomerWorkspace } from './CustomerWorkspace'

describe('CustomerWorkspace', () => {
  it(
    'renders the authenticated workspace structure',
    () => {
      render(<CustomerWorkspace />)

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
        screen.getByRole('heading', {
          level: 4,
          name: 'Find a payment',
        }),
      ).toBeInTheDocument()
    },
  )

  it(
    'provides in-page workspace navigation',
    () => {
      render(<CustomerWorkspace />)

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
})
