const minorUnitsPerPound = 100n

const gbpFormatter = new Intl.NumberFormat(
  'en-GB',
  {
    style: 'currency',
    currency: 'GBP',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  },
)

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
