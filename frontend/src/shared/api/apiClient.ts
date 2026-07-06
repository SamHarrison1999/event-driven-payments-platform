import {
  ApiProblemError,
  isApiProblem,
} from './apiProblem'
import type { JsonValidator } from './apiValidation'

const applicationJson = 'application/json'
const applicationProblemJson =
  'application/problem+json'

export type ApiMethod =
  | 'GET'
  | 'POST'
  | 'PUT'
  | 'PATCH'
  | 'DELETE'

export interface ApiRequestOptions {
  method?: ApiMethod
  headers?: HeadersInit
  body?: unknown
  signal?: AbortSignal
}

export interface ApiJsonRequestOptions<T>
  extends ApiRequestOptions {
  contractName: string
  validate: JsonValidator<T>
}

export interface ApiNoContentRequestOptions
  extends ApiRequestOptions {
  contractName: string
}

export class ApiHttpError extends Error {
  readonly status: number

  constructor(
    requestName: string,
    status: number,
  ) {
    super(
      `${requestName} request failed with HTTP status ${status}.`,
    )

    this.name = 'ApiHttpError'
    this.status = status
  }
}

export class ApiContractError extends Error {
  constructor(
    message: string,
    options?: ErrorOptions,
  ) {
    super(message, options)
    this.name = 'ApiContractError'
  }
}

export class ApiNetworkError extends Error {
  constructor(
    requestName: string,
    cause: unknown,
  ) {
    super(
      `${requestName} request failed before a response was received.`,
      {
        cause,
      },
    )

    this.name = 'ApiNetworkError'
  }
}

function resolveApiOrigin(): string {
  const configuredOrigin =
    import.meta.env.VITE_API_BASE_URL

  if (
    typeof configuredOrigin === 'string' &&
    configuredOrigin.trim().length > 0
  ) {
    return configuredOrigin
  }

  return window.location.origin
}

function resolveApiUrl(path: string): URL {
  return new URL(
    path,
    resolveApiOrigin(),
  )
}

function getMediaType(
  response: Response,
): string | null {
  const contentType =
    response.headers.get('content-type')

  if (!contentType) {
    return null
  }

  return (
    contentType
      .split(';', 1)[0]
      ?.trim()
      .toLowerCase() ?? null
  )
}

function isJsonMediaType(
  mediaType: string | null,
): boolean {
  return (
    mediaType === applicationJson ||
    (
      mediaType?.startsWith('application/') === true &&
      mediaType.endsWith('+json')
    )
  )
}

function createHeaders(
  options: ApiRequestOptions,
): Headers {
  const headers = new Headers(options.headers)

  if (!headers.has('Accept')) {
    headers.set(
      'Accept',
      `${applicationJson}, ${applicationProblemJson}`,
    )
  }

  if (
    options.body !== undefined &&
    !headers.has('Content-Type')
  ) {
    headers.set(
      'Content-Type',
      applicationJson,
    )
  }

  return headers
}

function serializeBody(
  body: unknown,
  contractName: string,
): string | undefined {
  if (body === undefined) {
    return undefined
  }

  try {
    return JSON.stringify(body)
  } catch (error) {
    throw new ApiContractError(
      `${contractName} request body could not be serialized as JSON.`,
      {
        cause: error,
      },
    )
  }
}

async function executeRequest(
  path: string,
  options: ApiRequestOptions,
  contractName: string,
): Promise<Response> {
  try {
    return await fetch(
      resolveApiUrl(path),
      {
        method: options.method ?? 'GET',
        headers: createHeaders(options),
        body: serializeBody(
          options.body,
          contractName,
        ),
        credentials: 'include',
        signal: options.signal,
      },
    )
  } catch (error) {
    if (
      error instanceof DOMException &&
      error.name === 'AbortError'
    ) {
      throw error
    }

    throw new ApiNetworkError(
      contractName,
      error,
    )
  }
}

async function parseJsonBody(
  response: Response,
  description: string,
): Promise<unknown> {
  const text = await response.text()

  if (text.trim().length === 0) {
    throw new ApiContractError(
      `${description} response body was empty.`,
    )
  }

  try {
    return JSON.parse(text) as unknown
  } catch (error) {
    throw new ApiContractError(
      `${description} response body was not valid JSON.`,
      {
        cause: error,
      },
    )
  }
}

async function throwResponseError(
  response: Response,
  contractName: string,
): Promise<never> {
  const mediaType = getMediaType(response)

  if (mediaType === applicationProblemJson) {
    const payload = await parseJsonBody(
      response,
      `${contractName} problem`,
    )

    if (
      !isApiProblem(payload) ||
      payload.status !== response.status
    ) {
      throw new ApiContractError(
        `${contractName} problem response did not match the expected contract.`,
      )
    }

    throw new ApiProblemError(payload)
  }

  throw new ApiHttpError(
    contractName,
    response.status,
  )
}

export async function apiRequestJson<T>(
  path: string,
  options: ApiJsonRequestOptions<T>,
): Promise<T> {
  const response = await executeRequest(
    path,
    options,
    options.contractName,
  )

  if (!response.ok) {
    return throwResponseError(
      response,
      options.contractName,
    )
  }

  const mediaType = getMediaType(response)

  if (
    !isJsonMediaType(mediaType) ||
    mediaType === applicationProblemJson
  ) {
    throw new ApiContractError(
      `${options.contractName} response used an unexpected content type.`,
    )
  }

  const payload = await parseJsonBody(
    response,
    options.contractName,
  )

  if (!options.validate(payload)) {
    throw new ApiContractError(
      `${options.contractName} response did not match the expected contract.`,
    )
  }

  return payload
}

export async function apiRequestNoContent(
  path: string,
  options: ApiNoContentRequestOptions,
): Promise<void> {
  const response = await executeRequest(
    path,
    options,
    options.contractName,
  )

  if (!response.ok) {
    return throwResponseError(
      response,
      options.contractName,
    )
  }

  if (response.status !== 204) {
    throw new ApiContractError(
      `${options.contractName} response did not use HTTP status 204.`,
    )
  }
}
