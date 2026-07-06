import {
  HttpResponse,
  http,
} from 'msw'
import {
  describe,
  expect,
  it,
} from 'vitest'

import { server } from '../../test/server'
import {
  ApiContractError,
  ApiHttpError,
  ApiNetworkError,
  apiRequestJson,
  apiRequestNoContent,
} from './apiClient'
import { ApiProblemError } from './apiProblem'
import { isJsonObject } from './apiValidation'

const endpoint =
  'http://localhost:5173/api/v1/test'

interface Greeting {
  message: string
}

function isGreeting(
  value: unknown,
): value is Greeting {
  return (
    isJsonObject(value) &&
    typeof value.message === 'string'
  )
}

describe('apiRequestJson', () => {
  it(
    'sends included credentials and accepts JSON responses',
    async () => {
      let credentials:
        | RequestCredentials
        | undefined
      let accept: string | null = null

      server.use(
        http.get(endpoint, ({ request }) => {
          credentials = request.credentials
          accept = request.headers.get('accept')

          return HttpResponse.json({
            message: 'hello',
          })
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).resolves.toEqual({
        message: 'hello',
      })

      expect(credentials).toBe('include')
      expect(accept).toBe(
        'application/json, application/problem+json',
      )
    },
  )

  it(
    'serializes request bodies as JSON',
    async () => {
      let contentType: string | null = null
      let body: unknown

      server.use(
        http.post(
          endpoint,
          async ({ request }) => {
            contentType =
              request.headers.get('content-type')
            body = await request.json()

            return HttpResponse.json({
              message: 'created',
            })
          },
        ),
      )

      await apiRequestJson(
        '/api/v1/test',
        {
          method: 'POST',
          body: {
            value: 42,
          },
          contractName: 'Greeting',
          validate: isGreeting,
        },
      )

      expect(contentType).toBe(
        'application/json',
      )
      expect(body).toEqual({
        value: 42,
      })
    },
  )

  it(
    'throws a typed API problem error',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(
            {
              type: 'urn:problem:test:invalid',
              title: 'Invalid request',
              status: 400,
              detail:
                'The request contains invalid fields.',
              code: 'TEST_INVALID',
              violations: [
                {
                  field: 'value',
                  message: 'Value is required.',
                },
              ],
            },
            {
              status: 400,
              headers: {
                'Content-Type':
                  'application/problem+json',
              },
            },
          )
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toMatchObject({
        name: 'ApiProblemError',
        status: 400,
        problem: {
          code: 'TEST_INVALID',
          violations: [
            {
              field: 'value',
              message: 'Value is required.',
            },
          ],
        },
      })

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toBeInstanceOf(
        ApiProblemError,
      )
    },
  )

  it(
    'rejects malformed problem responses',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json(
            {
              title: 'Missing status',
              detail: 'Malformed problem.',
            },
            {
              status: 400,
              headers: {
                'Content-Type':
                  'application/problem+json',
              },
            },
          )
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )

  it(
    'throws a typed HTTP error for an empty error response',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return new HttpResponse(null, {
            status: 503,
          })
        }),
      )

      const request = apiRequestJson(
        '/api/v1/test',
        {
          contractName: 'Greeting',
          validate: isGreeting,
        },
      )

      await expect(request).rejects.toBeInstanceOf(
        ApiHttpError,
      )

      await expect(request).rejects.toMatchObject({
        status: 503,
      })
    },
  )

  it(
    'rejects unexpected successful content types',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.text(
            '{"message":"hello"}',
            {
              headers: {
                'Content-Type': 'text/plain',
              },
            },
          )
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toThrow(
        'Greeting response used an unexpected content type.',
      )
    },
  )

  it(
    'rejects malformed successful JSON',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return new HttpResponse(
            '{"message":',
            {
              headers: {
                'Content-Type':
                  'application/json',
              },
            },
          )
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toThrow(
        'Greeting response body was not valid JSON.',
      )
    },
  )

  it(
    'rejects JSON that fails runtime validation',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.json({
            unexpected: true,
          })
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toThrow(
        'Greeting response did not match the expected contract.',
      )
    },
  )

  it(
    'throws a typed network error',
    async () => {
      server.use(
        http.get(endpoint, () => {
          return HttpResponse.error()
        }),
      )

      await expect(
        apiRequestJson(
          '/api/v1/test',
          {
            contractName: 'Greeting',
            validate: isGreeting,
          },
        ),
      ).rejects.toBeInstanceOf(
        ApiNetworkError,
      )
    },
  )
})

describe('apiRequestNoContent', () => {
  it('accepts an HTTP 204 response', async () => {
    server.use(
      http.delete(endpoint, () => {
        return new HttpResponse(null, {
          status: 204,
        })
      }),
    )

    await expect(
      apiRequestNoContent(
        '/api/v1/test',
        {
          method: 'DELETE',
          contractName: 'Delete greeting',
        },
      ),
    ).resolves.toBeUndefined()
  })

  it(
    'rejects a successful response with another status',
    async () => {
      server.use(
        http.delete(endpoint, () => {
          return HttpResponse.json({
            deleted: true,
          })
        }),
      )

      await expect(
        apiRequestNoContent(
          '/api/v1/test',
          {
            method: 'DELETE',
            contractName: 'Delete greeting',
          },
        ),
      ).rejects.toThrow(
        'Delete greeting response did not use HTTP status 204.',
      )
    },
  )
})
