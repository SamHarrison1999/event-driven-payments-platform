import {
  describe,
  expect,
  it,
} from 'vitest'

import { formatGbpMinorUnits } from './gbp'

describe('formatGbpMinorUnits', () => {
  it(
    'formats integer minor units as exact GBP',
    () => {
      expect(
        formatGbpMinorUnits(0),
      ).toBe('£0.00')

      expect(
        formatGbpMinorUnits(3),
      ).toBe('£0.03')

      expect(
        formatGbpMinorUnits(1250),
      ).toBe('£12.50')

      expect(
        formatGbpMinorUnits(5000),
      ).toBe('£50.00')
    },
  )

  it(
    'formats the largest supported safe integer without losing pence',
    () => {
      expect(
        formatGbpMinorUnits(
          Number.MAX_SAFE_INTEGER,
        ),
      ).toBe(
        '£90,071,992,547,409.91',
      )
    },
  )

  it(
    'rejects invalid minor-unit values',
    () => {
      expect(() => {
        formatGbpMinorUnits(-1)
      }).toThrow(
        'GBP minor units must be a non-negative safe integer.',
      )

      expect(() => {
        formatGbpMinorUnits(1.5)
      }).toThrow(RangeError)

      expect(() => {
        formatGbpMinorUnits(
          Number.MAX_SAFE_INTEGER + 1,
        )
      }).toThrow(RangeError)
    },
  )
})
