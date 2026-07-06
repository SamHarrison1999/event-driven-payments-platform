import {
  HttpResponse,
  http,
} from 'msw'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { server } from '../../test/server'
import { ApiContractError } from './apiClient'
import {
  clearCsrfToken,
  getCsrfHeaders,
  getCsrfToken,
} from './csrfToken'

const endpoint =
  'http://localhost:5173/api/v1/identity/csrf'

const token = {
  headerName: 'X-CSRF-TOKEN',
  parameterName: '_csrf',
  token: 'test-token',
}

beforeEach(() => {
  clearCsrfToken()
})

describe('getCsrfToken', () => {
  it(
    'shares and caches a valid in-memory token',
    async () => {
      let requests = 0

      server.use(
        http.get(endpoint, () => {
          requests += 1
          return HttpResponse.json(token)
        }),
      )

      const [first, second] =
        await Promise.all([
          getCsrfToken(),
          getCsrfToken(),
        ])

      const third = await getCsrfToken()

      expect(first).toEqual(token)
      expect(second).toEqual(token)
      expect(third).toEqual(token)
      expect(requests).toBe(1)
    },
  )

  it(
    'refetches after the in-memory token is cleared',
    async () => {
      let requests = 0

      server.use(
        http.get(endpoint, () => {
          requests += 1

          return HttpResponse.json({
            ...token,
            token: `token-${requests}`,
          })
        }),
      )

      await expect(
        getCsrfToken(),
      ).resolves.toMatchObject({
        token: 'token-1',
      })

      clearCsrfToken()

      await expect(
        getCsrfToken(),
      ).resolves.toMatchObject({
        token: 'token-2',
      })

      expect(requests).toBe(2)
    },
  )

  it(
    'creates the configured CSRF request header',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(token)
        }),
      )

      await expect(
        getCsrfHeaders(),
      ).resolves.toEqual({
        'X-CSRF-TOKEN': 'test-token',
      })
    },
  )

  it(
    'rejects a malformed CSRF contract',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
          })
        }),
      )

      await expect(
        getCsrfToken(),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
