import {
  ApiContractError,
  apiRequestJsonResponse,
} from '../../../shared/api/apiClient'
import {
  isSettlementDiscrepancy,
  type SettlementDiscrepancyDetail,
} from './settlement'

const strongVersionEtag = /^"(0|[1-9][0-9]*)"$/

export async function getSettlementDiscrepancy(
  discrepancyId: string,
  signal?: AbortSignal,
): Promise<SettlementDiscrepancyDetail> {
  const response =
    await apiRequestJsonResponse(
      `/api/v1/settlement-discrepancies/${discrepancyId}`,
      {
        contractName:
          'Settlement discrepancy detail',
        validate: isSettlementDiscrepancy,
        signal,
      },
    )
  const etag = response.headers.get('etag')

  if (
    etag === null ||
    !strongVersionEtag.test(etag) ||
    etag !==
      `"${response.data.version.toString()}"`
  ) {
    throw new ApiContractError(
      'Settlement discrepancy response did not include its expected strong ETag.',
    )
  }

  return {
    discrepancy: response.data,
    etag,
  }
}
