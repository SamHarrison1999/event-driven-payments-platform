import { apiRequestJson } from './apiClient'
import {
  isJsonObject,
  isNonEmptyString,
} from './apiValidation'

export interface CsrfToken {
  headerName: string
  parameterName: string
  token: string
}

let cachedToken: CsrfToken | null = null
let pendingToken:
  | Promise<CsrfToken>
  | null = null
let cacheGeneration = 0

function isCsrfToken(
  value: unknown,
): value is CsrfToken {
  return (
    isJsonObject(value) &&
    isNonEmptyString(value.headerName) &&
    isNonEmptyString(value.parameterName) &&
    isNonEmptyString(value.token)
  )
}

export function clearCsrfToken(): void {
  cacheGeneration += 1
  cachedToken = null
  pendingToken = null
}

export function getCsrfToken(): Promise<CsrfToken> {
  if (cachedToken) {
    return Promise.resolve(cachedToken)
  }

  if (pendingToken) {
    return pendingToken
  }

  const requestGeneration = cacheGeneration

  const request = apiRequestJson(
    '/api/v1/identity/csrf',
    {
      contractName: 'CSRF token',
      validate: isCsrfToken,
    },
  ).then((token) => {
    if (
      cacheGeneration === requestGeneration
    ) {
      cachedToken = token
    }

    return token
  }).finally(() => {
    if (pendingToken === request) {
      pendingToken = null
    }
  })

  pendingToken = request

  return request
}

export async function getCsrfHeaders(): Promise<
  Record<string, string>
> {
  const token = await getCsrfToken()

  return {
    [token.headerName]: token.token,
  }
}
