import {
  useMutation,
  useQueryClient,
} from '@tanstack/react-query'

import { isApiErrorWithStatus } from '../../../shared/api/apiError'
import { expireCurrentSession } from '../../identity/hooks/expireCurrentSession'
import {
  replayDeadLetter,
  type ReplayDeadLetterInput,
} from '../api/replayDeadLetter'
import { deadLetterQueryKeys } from './deadLetterQueryKeys'

export function useReplayDeadLetter() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (
      input: ReplayDeadLetterInput,
    ) => replayDeadLetter(input),
    onError: (error) => {
      if (isApiErrorWithStatus(error, 401)) {
        expireCurrentSession(queryClient)
      }
    },
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: deadLetterQueryKeys.all,
      }),
    retry: false,
  })
}
