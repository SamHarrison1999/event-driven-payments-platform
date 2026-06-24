import {
  HttpResponse,
  http,
} from 'msw'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { server } from '../../../test/server'
import {
  getSystemInfo,
  type SystemInfo,
} from './getSystemInfo'

const endpoint =
  'http://localhost:5173/api/v1/system/info'

const validSystemInfo: SystemInfo = {
  name: 'Event-Driven Payments and Reconciliation Platform',
  description:
    'Educational simulation of payment processing and settlement reconciliation',
  version: '0.0.1-SNAPSHOT',
  educational: true,
  realMoneyProcessing: false,
}

describe('getSystemInfo', () => {
  it('returns a valid system-information response', async () => {
    server.use(
      http.get(endpoint, () => {
        return HttpResponse.json(validSystemInfo)
      }),
    )

    await expect(getSystemInfo()).resolves.toEqual(
      validSystemInfo,
    )
  })

  it('rejects unsuccessful HTTP responses', async () => {
    server.use(
      http.get(endpoint, () => {
        return new HttpResponse(null, {
          status: 503,
        })
      }),
    )

    await expect(getSystemInfo()).rejects.toThrow(
      'System information request failed with status 503.',
    )
  })

  it('rejects responses that do not match the API contract', async () => {
    server.use(
      http.get(endpoint, () => {
        return HttpResponse.json({
          name: 'Incomplete response',
        })
      }),
    )

    await expect(getSystemInfo()).rejects.toThrow(
      'System information response did not match the expected contract.',
    )
  })
})
