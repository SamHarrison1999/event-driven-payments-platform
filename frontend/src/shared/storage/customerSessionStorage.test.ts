import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import {
  clearCustomerSessionStorage,
  customerSessionStorageKeys,
} from './customerSessionStorage'

beforeEach(() => {
  window.sessionStorage.clear()
})

describe('clearCustomerSessionStorage', () => {
  it(
    'removes bounded customer session state without clearing unrelated values',
    () => {
      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'payment-state',
      )
      window.sessionStorage.setItem(
        'unrelated',
        'keep-me',
      )

      clearCustomerSessionStorage()

      expect(
        window.sessionStorage.getItem(
          customerSessionStorageKeys
            .paymentSubmission,
        ),
      ).toBeNull()

      expect(
        window.sessionStorage.getItem(
          'unrelated',
        ),
      ).toBe('keep-me')
    },
  )
})
