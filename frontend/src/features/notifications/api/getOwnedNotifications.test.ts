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
import type { PaymentNotification } from './notification'
import { getOwnedNotifications } from './getOwnedNotifications'

const endpoint =
  'http://localhost:5173/api/v1/notifications'

const notification: PaymentNotification = {
  notificationId:
    '11111111-1111-4111-8111-111111111111',
  paymentId:
    '22222222-2222-4222-8222-222222222222',
  amountMinorUnits: 1250,
  currency: 'GBP',
  paymentCompletedAt:
    '2026-07-23T14:59:00Z',
  status: 'DELIVERED',
  createdAt: '2026-07-23T15:00:00Z',
  deliveredAt: '2026-07-23T15:00:01Z',
}

describe('getOwnedNotifications', () => {
  it(
    'returns the authenticated customer notifications',
    async () => {
      server.use(
        http.get(endpoint, ({ request }) => {
          expect(
            new URL(request.url).searchParams.get(
              'limit',
            ),
          ).toBe('50')

          return HttpResponse.json([
            notification,
          ])
        }),
      )

      await expect(
        getOwnedNotifications(),
      ).resolves.toEqual([notification])
    },
  )

  it.each([
    {
      name: 'unsafe amount',
      override: {
        amountMinorUnits:
          Number.MAX_SAFE_INTEGER + 1,
      },
    },
    {
      name: 'unknown status',
      override: {
        status: 'SENT',
      },
    },
    {
      name: 'invalid timestamp',
      override: {
        createdAt: '23 July 2026',
      },
    },
    {
      name: 'invalid delivery timestamp',
      override: {
        deliveredAt: 'tomorrow',
      },
    },
  ])(
    'rejects a response with $name',
    async ({ override }) => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            {
              ...notification,
              ...override,
            },
          ])
        }),
      )

      await expect(
        getOwnedNotifications(),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
