import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  clearCsrfToken,
  getCsrfHeaders,
} from '../../../shared/api/csrfToken'

export interface RegistrationDetails {
  email: string
  password: string
}

export interface CustomerRegistration {
  id: string
  email: string
  status: string
  roles: string[]
  createdAt: string
}

function isCustomerRegistration(
  value: unknown,
): value is CustomerRegistration {
  if (
    typeof value !== 'object' ||
    value === null
  ) {
    return false
  }

  const registration =
    value as Record<string, unknown>

  return (
    typeof registration.id === 'string' &&
    typeof registration.email === 'string' &&
    typeof registration.status === 'string' &&
    Array.isArray(registration.roles) &&
    registration.roles.every(
      (role) => typeof role === 'string',
    ) &&
    typeof registration.createdAt === 'string'
  )
}


export async function registerCustomer(
  details: RegistrationDetails,
): Promise<CustomerRegistration> {
  const csrfHeaders = await getCsrfHeaders()

  const registration = await apiRequestJson(
    '/api/v1/identity/registrations',
    {
      method: 'POST',
      headers: csrfHeaders,
      body: details,
      contractName: 'Create account',
      expectedStatus: 201,
      validate: isCustomerRegistration,
    },
  )

  clearCsrfToken()

  return registration
}