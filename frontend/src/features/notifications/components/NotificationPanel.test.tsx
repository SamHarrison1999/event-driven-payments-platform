import {
  HttpResponse,
  http,
} from 'msw'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { renderWithQueryClient } from '../../../test/renderWithQueryClient'
import { server } from '../../../test/server'
import type { PaymentNotification } from '../api/notification'
import { NotificationPanel } from './NotificationPanel'

const endpoint =
  'http://localhost:5173/api/v1/notifications'

const notification: PaymentNotification = {
  notificationId:
    '11111111-1111-4111-8111-111111111111',
  paymentId:
    '22222222-2222-4222-8222-22222222bc28',
  amountMinorUnits: 1250,
  currency: 'GBP',
  paymentCompletedAt:
    '2026-07-23T14:59:00Z',
  status: 'DELIVERED',
  createdAt: '2026-07-23T15:00:00Z',
  deliveredAt: '2026-07-23T15:00:01Z',
}

describe('NotificationPanel', () => {
  it(
    'displays owned payment notifications',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([
            notification,
          ])
        }),
      )

      renderWithQueryClient(
        <NotificationPanel />,
      )

      expect(
        await screen.findByRole('list', {
          name: 'Payment notifications',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByRole('heading', {
          level: 5,
          name: 'Payment 2222BC28',
        }),
      ).toBeInTheDocument()

      expect(
        screen.getByText('£12.50'),
      ).toBeInTheDocument()

      expect(
        screen.getByLabelText(
          'Status: Delivered',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('1 notification'),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows an empty notification state',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json([])
        }),
      )

      renderWithQueryClient(
        <NotificationPanel />,
      )

      expect(
        await screen.findByText(
          'No notifications yet',
        ),
      ).toBeInTheDocument()

      expect(
        screen.getByText('0 notifications'),
      ).toBeInTheDocument()
    },
  )

  it(
    'allows an unavailable request to be retried',
    async () => {
      let attempts = 0

      server.use(
        http.get(endpoint, () => {
          attempts += 1

          if (attempts === 1) {
            return new HttpResponse(null, {
              status: 503,
            })
          }

          return HttpResponse.json([
            notification,
          ])
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(
        <NotificationPanel />,
      )

      await user.click(
        await screen.findByRole(
          'button',
          {
            name: 'Try again',
          },
        ),
      )

      expect(
        await screen.findByText('£12.50'),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )
})
