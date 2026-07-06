import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import {
  customerSessionStorageKeys,
} from '../../../shared/storage/customerSessionStorage'
import type { PaymentDraft } from '../model/paymentDraft'
import {
  discardPaymentSubmissionEnvelopeForDifferentUser,
  getOrCreatePaymentSubmissionEnvelope,
  resolvePaymentSubmissionEnvelope,
} from './paymentSubmissionEnvelope'

const userId =
  'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
const otherUserId =
  'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'

const draft: PaymentDraft = {
  sourceAccountId:
    '11111111-1111-4111-8111-111111111111',
  destinationAccountId:
    '22222222-2222-4222-8222-222222222222',
  amountMinorUnits: 1050,
}

beforeEach(() => {
  window.sessionStorage.clear()
})

describe(
  'payment submission envelope',
  () => {
    it(
      'reuses one key for the exact unresolved draft',
      () => {
        const first =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            draft,
            1_000,
          )

        const second =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            {
              ...draft,
            },
            2_000,
          )

        expect(
          second.idempotencyKey,
        ).toBe(first.idempotencyKey)

        expect(second).toEqual(first)
      },
    )

    it(
      'creates a new key when the logical draft changes',
      () => {
        const first =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            draft,
            1_000,
          )

        const second =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            {
              ...draft,
              amountMinorUnits: 1051,
            },
            2_000,
          )

        expect(
          second.idempotencyKey,
        ).not.toBe(first.idempotencyKey)

        expect(
          second.draft.amountMinorUnits,
        ).toBe(1051)
      },
    )

    it(
      'clears malformed and expired envelopes before creating a key',
      () => {
        window.sessionStorage.setItem(
          customerSessionStorageKeys
            .paymentSubmission,
          '{not-json',
        )

        const malformedReplacement =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            draft,
            1_000,
          )

        window.sessionStorage.setItem(
          customerSessionStorageKeys
            .paymentSubmission,
          JSON.stringify({
            ...malformedReplacement,
            createdAtEpochMs: 1_000,
          }),
        )

        const expiredReplacement =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            draft,
            24 * 60 * 60 * 1000 +
              1_001,
          )

        expect(
          expiredReplacement.idempotencyKey,
        ).not.toBe(
          malformedReplacement.idempotencyKey,
        )
      },
    )

    it(
      'discards retry state owned by another identity',
      () => {
        getOrCreatePaymentSubmissionEnvelope(
          userId,
          draft,
          1_000,
        )

        discardPaymentSubmissionEnvelopeForDifferentUser(
          otherUserId,
          2_000,
        )

        const replacement =
          getOrCreatePaymentSubmissionEnvelope(
            otherUserId,
            draft,
            3_000,
          )

        expect(
          replacement.identityUserId,
        ).toBe(otherUserId)
      },
    )

    it(
      'resolves only the matching idempotency key',
      () => {
        const envelope =
          getOrCreatePaymentSubmissionEnvelope(
            userId,
            draft,
            1_000,
          )

        resolvePaymentSubmissionEnvelope(
          'different-key',
          2_000,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).not.toBeNull()

        resolvePaymentSubmissionEnvelope(
          envelope.idempotencyKey,
          2_000,
        )

        expect(
          window.sessionStorage.getItem(
            customerSessionStorageKeys
              .paymentSubmission,
          ),
        ).toBeNull()
      },
    )
  },
)
