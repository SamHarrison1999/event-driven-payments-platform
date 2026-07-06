const minorUnitsPerPound = 100n
const maximumSafeMinorUnits =
  BigInt(Number.MAX_SAFE_INTEGER)

const gbpFormatter = new Intl.NumberFormat(
  'en-GB',
  {
    style: 'currency',
    currency: 'GBP',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  },
)

export type GbpAmountInputErrorCode =
  | 'required'
  | 'format'
  | 'positive'
  | 'range'

export type GbpAmountParseResult =
  | {
      ok: true
      minorUnits: number
    }
  | {
      ok: false
      code: GbpAmountInputErrorCode
      message: string
    }

export function formatGbpMinorUnits(
  minorUnits: number,
): string {
  if (
    !Number.isSafeInteger(minorUnits) ||
    minorUnits < 0
  ) {
    throw new RangeError(
      'GBP minor units must be a non-negative safe integer.',
    )
  }

  const exactMinorUnits = BigInt(minorUnits)
  const wholePounds =
    exactMinorUnits / minorUnitsPerPound
  const pence = (
    exactMinorUnits % minorUnitsPerPound
  )
    .toString()
    .padStart(2, '0')

  return gbpFormatter
    .formatToParts(wholePounds)
    .map((part) => {
      if (part.type === 'fraction') {
        return pence
      }

      return part.value
    })
    .join('')
}

export function parsePositiveGbpAmount(
  value: string,
): GbpAmountParseResult {
  const trimmedValue = value.trim()

  if (trimmedValue.length === 0) {
    return {
      ok: false,
      code: 'required',
      message: 'Enter a payment amount.',
    }
  }

  const amountMatch =
    /^(\d+)(?:\.(\d{1,2}))?$/.exec(
      trimmedValue,
    )

  if (amountMatch === null) {
    return {
      ok: false,
      code: 'format',
      message:
        'Enter a GBP amount using whole pounds and up to two decimal places.',
    }
  }

  const wholePounds = BigInt(
    amountMatch[1],
  )
  const pence = BigInt(
    (amountMatch[2] ?? '')
      .padEnd(2, '0'),
  )
  const minorUnits =
    wholePounds * minorUnitsPerPound +
    pence

  if (minorUnits === 0n) {
    return {
      ok: false,
      code: 'positive',
      message:
        'Enter an amount greater than £0.00.',
    }
  }

  if (
    minorUnits > maximumSafeMinorUnits
  ) {
    return {
      ok: false,
      code: 'range',
      message:
        'Enter a smaller GBP amount.',
    }
  }

  return {
    ok: true,
    minorUnits: Number(minorUnits),
  }
}
