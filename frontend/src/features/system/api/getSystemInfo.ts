export interface SystemInfo {
  name: string
  description: string
  version: string
  educational: boolean
  realMoneyProcessing: boolean
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isSystemInfo(value: unknown): value is SystemInfo {
  if (!isObject(value)) {
    return false
  }

  return (
    typeof value.name === 'string' &&
    typeof value.description === 'string' &&
    typeof value.version === 'string' &&
    typeof value.educational === 'boolean' &&
    typeof value.realMoneyProcessing === 'boolean'
  )
}

function resolveApiOrigin(): string {
  const configuredOrigin = import.meta.env.VITE_API_BASE_URL

  if (
    typeof configuredOrigin === 'string' &&
    configuredOrigin.trim().length > 0
  ) {
    return configuredOrigin
  }

  return window.location.origin
}

export async function getSystemInfo(
  signal?: AbortSignal,
): Promise<SystemInfo> {
  const url = new URL(
    '/api/v1/system/info',
    resolveApiOrigin(),
  )

  const response = await fetch(url, {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
    signal,
  })

  if (!response.ok) {
    throw new Error(
      `System information request failed with status ${response.status}.`,
    )
  }

  const payload: unknown = await response.json()

  if (!isSystemInfo(payload)) {
    throw new Error(
      'System information response did not match the expected contract.',
    )
  }

  return payload
}
