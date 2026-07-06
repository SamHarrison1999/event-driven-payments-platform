import { apiRequestJson } from '../../../shared/api/apiClient'
import {
  isCustomerAccountList,
  type CustomerAccount,
} from './customerAccount'

export function getOwnedAccounts(
  signal?: AbortSignal,
): Promise<CustomerAccount[]> {
  return apiRequestJson(
    '/api/v1/accounts',
    {
      contractName: 'Owned accounts',
      validate: isCustomerAccountList,
      signal,
    },
  )
}
