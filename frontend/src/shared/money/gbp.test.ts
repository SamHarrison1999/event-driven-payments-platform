import {
  describe,
  expect,
  it,
} from 'vitest'

import {
  formatGbpMinorUnits,
  parsePositiveGbpAmount,
} from './gbp'

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

describe('parsePositiveGbpAmount', () => {
  it.each([
    ['10', 1000],
    ['10.5', 1050],
    ['10.50', 1050],
    ['0.01', 1],
    ['00010.05', 1005],
    [
      '90071992547409.91',
      Number.MAX_SAFE_INTEGER,
    ],
  ])(
    'parses %s directly to exact minor units',
    (value, expectedMinorUnits) => {
      expect(
        parsePositiveGbpAmount(value),
      ).toEqual({
        ok: true,
        minorUnits: expectedMinorUnits,
      })
    },
  )

  it(
    'ignores surrounding whitespace',
    () => {
      expect(
        parsePositiveGbpAmount(
          '  12.34  ',
        ),
      ).toEqual({
        ok: true,
        minorUnits: 1234,
      })
    },
  )

  it.each([
    '10.',
    '.50',
    '1.234',
    '1e2',
    '-1',
    '+1',
    '£10',
    '1,000.00',
  ])(
    'rejects malformed amount %s',
    (value) => {
      expect(
        parsePositiveGbpAmount(value),
      ).toMatchObject({
        ok: false,
        code: 'format',
      })
    },
  )

  it.each(['0', '0.0', '0.00'])(
    'rejects non-positive amount %s',
    (value) => {
      expect(
        parsePositiveGbpAmount(value),
      ).toEqual({
        ok: false,
        code: 'positive',
        message:
          'Enter an amount greater than £0.00.',
      })
    },
  )

  it(
    'rejects an empty amount',
    () => {
      expect(
        parsePositiveGbpAmount('  '),
      ).toEqual({
        ok: false,
        code: 'required',
        message:
          'Enter a payment amount.',
      })
    },
  )

  it(
    'rejects an amount outside the safe integer range',
    () => {
      expect(
        parsePositiveGbpAmount(
          '90071992547409.92',
        ),
      ).toEqual({
        ok: false,
        code: 'range',
        message:
          'Enter a smaller GBP amount.',
      })
    },
  )
})
