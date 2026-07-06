import {
  HttpResponse,
  http,
} from 'msw'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { ApiContractError } from '../../../shared/api/apiClient'
import { server } from '../../../test/server'
import type { CustomerAccount } from './customerAccount'
import { getOwnedAccounts } from './getOwnedAccounts'

const endpoint =
  'http://localhost:5173/api/v1/accounts'

const account: CustomerAccount = {
  id:
    '4af96ca9-5012-4c4a-b52e-e052d3e977b9',
  customerId:
    'f56ff408-f9b6-4a7b-a319-b56907fa8679',
  currency: 'GBP',
  balanceMinorUnits: 1250,
  status: 'ACTIVE',
  createdAt: '2026-06-29T09:00:00Z',
  updatedAt: '2026-06-29T09:30:00Z',
  version: 2,
}

describe('getOwnedAccounts', () => {
  it(
    'returns the authenticated customer accounts',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            account,
            {
              ...account,
              id:
                '2cc61ee3-81dc-4c81-bf4e-baa82b68bc28',
              balanceMinorUnits: 5000,
              status: 'FROZEN',
            },
          ])
        }),
      )

      await expect(
        getOwnedAccounts(),
      ).resolves.toEqual([
        account,
        {
          ...account,
          id:
            '2cc61ee3-81dc-4c81-bf4e-baa82b68bc28',
          balanceMinorUnits: 5000,
          status: 'FROZEN',
        },
      ])
    },
  )

  it(
    'accepts an empty owned-account collection',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([])
        }),
      )

      await expect(
        getOwnedAccounts(),
      ).resolves.toEqual([])
    },
  )

  it.each([
    {
      name: 'unsafe balance',
      override: {
        balanceMinorUnits:
          Number.MAX_SAFE_INTEGER + 1,
      },
    },
    {
      name: 'unknown status',
      override: {
        status: 'PENDING',
      },
    },
    {
      name: 'invalid timestamp',
      override: {
        updatedAt: '29 June 2026',
      },
    },
    {
      name: 'negative version',
      override: {
        version: -1,
      },
    },
  ])(
    'rejects a response with $name',
    async ({ override }) => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            {
              ...account,
              ...override,
            },
          ])
        }),
      )

      await expect(
        getOwnedAccounts(),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
