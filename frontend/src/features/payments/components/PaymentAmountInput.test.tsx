import { useState } from 'react'
import {
  render,
  screen,
} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { PaymentAmountInput } from './PaymentAmountInput'

function PaymentAmountInputHarness() {
  const [value, setValue] =
    useState('')

  return (
    <PaymentAmountInput
      onChange={setValue}
      value={value}
    />
  )
}

describe('PaymentAmountInput', () => {
  it(
    'previews an exact GBP amount and minor units',
    async () => {
      const user = userEvent.setup()

      render(
        <PaymentAmountInputHarness />,
      )

      const input = screen.getByRole(
        'textbox',
        {
          name: 'Payment amount',
        },
      )

      await user.type(input, '10.5')

      expect(input).toHaveValue('10.5')

      expect(
        screen.getByText('£10.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByText(
          '1050 minor units',
        ),
      ).toBeInTheDocument()

      expect(input).not.toHaveAttribute(
        'aria-invalid',
      )
    },
  )

  it(
    'shows a format error after the field loses focus',
    async () => {
      const user = userEvent.setup()

      render(
        <PaymentAmountInputHarness />,
      )

      const input = screen.getByRole(
        'textbox',
        {
          name: 'Payment amount',
        },
      )

      await user.type(
        input,
        '1.234',
      )
      await user.tab()

      expect(
        screen.getByRole('alert'),
      ).toHaveTextContent(
        'Enter a GBP amount using whole pounds and up to two decimal places.',
      )

      expect(input).toHaveAttribute(
        'aria-invalid',
        'true',
      )
    },
  )

  it(
    'requires a positive amount and clears the error when corrected',
    async () => {
      const user = userEvent.setup()

      render(
        <PaymentAmountInputHarness />,
      )

      const input = screen.getByRole(
        'textbox',
        {
          name: 'Payment amount',
        },
      )

      await user.type(input, '0')
      await user.tab()

      expect(
        screen.getByRole('alert'),
      ).toHaveTextContent(
        'Enter an amount greater than £0.00.',
      )

      await user.click(input)
      await user.clear(input)
      await user.type(input, '0.01')

      expect(
        screen.queryByRole('alert'),
      ).not.toBeInTheDocument()

      expect(
        screen.getByText('£0.01'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('1 minor units'),
      ).toBeInTheDocument()
    },
  )
})
