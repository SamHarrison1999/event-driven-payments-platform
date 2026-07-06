import {
  describe,
  expect,
  it,
} from 'vitest'

import { isUuid } from './uuid'

describe('isUuid', () => {
  it('accepts canonical UUID strings', () => {
    expect(
      isUuid(
        '2f1f55da-5793-4a75-aeb5-c20f69f16949',
      ),
    ).toBe(true)

    expect(
      isUuid(
        '2F1F55DA-5793-4A75-AEB5-C20F69F16949',
      ),
    ).toBe(true)
  })

  it('rejects malformed identifiers', () => {
    expect(isUuid('not-a-uuid')).toBe(false)

    expect(
      isUuid(
        '2f1f55da57934a75aeb5c20f69f16949',
      ),
    ).toBe(false)

    expect(isUuid(null)).toBe(false)
  })
})
