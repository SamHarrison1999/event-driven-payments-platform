import {
  ApiContractError,
  apiRequestJsonResponse,
} from '../../../shared/api/apiClient'
import { getCsrfHeaders } from '../../../shared/api/csrfToken'
import {
  isSettlementImport,
  type SettlementImport,
} from './settlement'

export async function uploadSettlementFile(
  file: File,
): Promise<SettlementImport> {
  const formData = new FormData()
  formData.append('file', file, file.name)

  const csrfHeaders = await getCsrfHeaders()
  const response =
    await apiRequestJsonResponse(
      '/api/v1/settlement-imports',
      {
        method: 'POST',
        headers: csrfHeaders,
        body: formData,
        contractName: 'Settlement import',
        validate: isSettlementImport,
      },
    )

  if (
    response.status !== 200 &&
    response.status !== 201
  ) {
    throw new ApiContractError(
      'Settlement import response used an unexpected HTTP status.',
    )
  }

  if (
    response.data.existingImport !==
      (response.status === 200)
  ) {
    throw new ApiContractError(
      'Settlement import response status did not match its replay flag.',
    )
  }

  return response.data
}
