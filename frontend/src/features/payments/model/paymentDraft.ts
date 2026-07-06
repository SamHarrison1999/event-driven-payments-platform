import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { parsePositiveGbpAmount } from '../../../shared/money/gbp'

export interface PaymentDraftFields {
  sourceAccountId: string
  destinationAccountId: string
  amount: string
}

export interface PaymentDraft {
  sourceAccountId: string
  destinationAccountId: string
  amountMinorUnits: number
}

export interface PaymentDraftErrors {
  sourceAccountId?: string
  destinationAccountId?: string
  amount?: string
}

export type PaymentDraftValidationResult =
  | {
      ok: true
      draft: PaymentDraft
    }
  | {
      ok: false
      errors: PaymentDraftErrors
    }

function findActiveAccount(
  accounts: CustomerAccount[],
  accountId: string,
): CustomerAccount | undefined {
  return accounts.find(
    (account) =>
      account.id === accountId &&
      account.status === 'ACTIVE',
  )
}

export function validatePaymentDraft(
  fields: PaymentDraftFields,
  accounts: CustomerAccount[],
): PaymentDraftValidationResult {
  const errors: PaymentDraftErrors = {}

  const sourceAccount =
    fields.sourceAccountId.length === 0
      ? undefined
      : findActiveAccount(
          accounts,
          fields.sourceAccountId,
        )

  const destinationAccount =
    fields.destinationAccountId.length === 0
      ? undefined
      : findActiveAccount(
          accounts,
          fields.destinationAccountId,
        )

  if (fields.sourceAccountId.length === 0) {
    errors.sourceAccountId =
      'Choose a source account.'
  } else if (sourceAccount === undefined) {
    errors.sourceAccountId =
      'Choose an active source account.'
  }

  if (
    fields.destinationAccountId.length === 0
  ) {
    errors.destinationAccountId =
      'Choose a destination account.'
  } else if (
    destinationAccount === undefined
  ) {
    errors.destinationAccountId =
      'Choose an active destination account.'
  }

  if (
    fields.sourceAccountId.length > 0 &&
    fields.sourceAccountId ===
      fields.destinationAccountId
  ) {
    errors.destinationAccountId =
      'Choose a different destination account.'
  }

  const parsedAmount =
    parsePositiveGbpAmount(fields.amount)

  if (!parsedAmount.ok) {
    errors.amount = parsedAmount.message
  } else if (
    sourceAccount !== undefined &&
    parsedAmount.minorUnits >
      sourceAccount.balanceMinorUnits
  ) {
    errors.amount =
      'The payment amount exceeds the source account balance.'
  }

  if (
    errors.sourceAccountId !== undefined ||
    errors.destinationAccountId !==
      undefined ||
    errors.amount !== undefined
  ) {
    return {
      ok: false,
      errors,
    }
  }

  if (
    sourceAccount === undefined ||
    destinationAccount === undefined ||
    !parsedAmount.ok
  ) {
    throw new Error(
      'Validated payment draft is incomplete.',
    )
  }

  return {
    ok: true,
    draft: {
      sourceAccountId: sourceAccount.id,
      destinationAccountId:
        destinationAccount.id,
      amountMinorUnits:
        parsedAmount.minorUnits,
    },
  }
}
