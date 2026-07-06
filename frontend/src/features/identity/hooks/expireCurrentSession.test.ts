import { QueryClient } from '@tanstack/react-query'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import {
  customerSessionStorageKeys,
} from '../../../shared/storage/customerSessionStorage'
import { expireCurrentSession } from './expireCurrentSession'
import { identityQueryKeys } from './identityQueryKeys'

beforeEach(() => {
  window.sessionStorage.clear()
})

describe('expireCurrentSession', () => {
  it(
    'clears private query data but retains unresolved retry state',
    () => {
      const queryClient = new QueryClient()

      queryClient.setQueryData(
        identityQueryKeys.session,
        {
          userId:
            'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          email: 'customer@example.com',
          roles: ['CUSTOMER'],
        },
      )

      queryClient.setQueryData(
        ['accounts'],
        ['private-account-data'],
      )

      queryClient.setQueryData(
        ['payments', 'detail', 'payment-id'],
        {
          private: true,
        },
      )

      window.sessionStorage.setItem(
        customerSessionStorageKeys
          .paymentSubmission,
        'unresolved-payment',
      )

      expireCurrentSession(queryClient)

      expect(
        queryClient.getQueryData(
          identityQueryKeys.session,
        ),
      ).toBeNull()

      expect(
        queryClient.getQueryData(
          ['accounts'],
        ),
      ).toBeUndefined()

      expect(
        queryClient.getQueryData(
          [
            'payments',
            'detail',
            'payment-id',
          ],
        ),
      ).toBeUndefined()

      expect(
        window.sessionStorage.getItem(
          customerSessionStorageKeys
            .paymentSubmission,
        ),
      ).toBe('unresolved-payment')
    },
  )
})
