import { apiRequestJson } from '../../../shared/api/apiClient'
import { isJsonObject } from '../../../shared/api/apiValidation'

export interface SystemInfo {
  name: string
  description: string
  version: string
  educational: boolean
  realMoneyProcessing: boolean
}

function isSystemInfo(
  value: unknown,
): value is SystemInfo {
  if (!isJsonObject(value)) {
    return false
  }

  return (
    typeof value.name === 'string' &&
    typeof value.description === 'string' &&
    typeof value.version === 'string' &&
    typeof value.educational === 'boolean' &&
    typeof value.realMoneyProcessing ===
      'boolean'
  )
}

export function getSystemInfo(
  signal?: AbortSignal,
): Promise<SystemInfo> {
  return apiRequestJson(
    '/api/v1/system/info',
    {
      contractName: 'System information',
      validate: isSystemInfo,
      signal,
    },
  )
}
