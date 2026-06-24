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

import App from './App'
import type { SystemInfo } from './features/system/api/getSystemInfo'
import { renderWithQueryClient } from './test/renderWithQueryClient'
import { server } from './test/server'

const endpoint =
  'http://localhost:5173/api/v1/system/info'

const systemInfo: SystemInfo = {
  name: 'Event-Driven Payments and Reconciliation Platform',
  description:
    'Educational simulation of payment processing and settlement reconciliation',
  version: '0.0.1-SNAPSHOT',
  educational: true,
  realMoneyProcessing: false,
}

describe('App', () => {
  it(
    'renders the platform shell and connected system information',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(systemInfo)
        }),
      )

      renderWithQueryClient(<App />)

      expect(
        screen.getByRole('heading', {
          level: 1,
          name: 'Payments operations workspace',
        }),
      ).toBeInTheDocument()

      expect(
        await screen.findByText(systemInfo.name),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Connected'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Educational only'),
      ).toBeInTheDocument()

      expect(
        screen.getByText('Disabled'),
      ).toBeInTheDocument()
    },
  )

  it(
    'shows an accessible error when the backend is unavailable',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return new HttpResponse(null, {
            status: 503,
          })
        }),
      )

      renderWithQueryClient(<App />)

      const alert = await screen.findByRole('alert')

      expect(alert).toHaveTextContent(
        'Backend unavailable',
      )

      expect(
        screen.getByRole('button', {
          name: 'Try again',
        }),
      ).toBeEnabled()
    },
  )

  it(
    'allows the user to retry a failed request',
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

          return HttpResponse.json(systemInfo)
        }),
      )

      const user = userEvent.setup()

      renderWithQueryClient(<App />)

      const retryButton =
        await screen.findByRole('button', {
          name: 'Try again',
        })

      await user.click(retryButton)

      expect(
        await screen.findByText(systemInfo.name),
      ).toBeInTheDocument()

      expect(attempts).toBe(2)
    },
  )
})
