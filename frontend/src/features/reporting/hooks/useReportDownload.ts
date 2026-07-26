import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import {
  downloadReport,
  type ReportFamily,
} from '../api/downloadReport'
import type { ReportWindow } from '../api/getOperationalSummary'

interface ReportDownloadRequest {
  family: ReportFamily
  window: ReportWindow
}

export function useReportDownload() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (
      request: ReportDownloadRequest,
    ) =>
      downloadReport(
        request.family,
        request.window,
      ),
    onError: (error) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
      }
    },
    retry: false,
  })
}
