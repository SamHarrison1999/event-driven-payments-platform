import {
  describe,
  expect,
  it,
} from 'vitest'

import type { CustomerAccount } from '../../accounts/api/customerAccount'
import { validatePaymentDraft } from './paymentDraft'

const sourceAccount: CustomerAccount = {
  id:
    '11111111-1111-4111-8111-111111111111',
  customerId:
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  currency: 'GBP',
  balanceMinorUnits: 1250,
  status: 'ACTIVE',
  createdAt: '2026-07-01T09:00:00Z',
  updatedAt: '2026-07-01T09:00:00Z',
  version: 0,
}

const destinationAccount: CustomerAccount = {
  ...sourceAccount,
  id:
    '22222222-2222-4222-8222-222222222222',
  balanceMinorUnits: 5000,
}

const frozenAccount: CustomerAccount = {
  ...sourceAccount,
  id:
    '33333333-3333-4333-8333-333333333333',
  status: 'FROZEN',
}

const accounts = [
  sourceAccount,
  destinationAccount,
  frozenAccount,
]

describe('validatePaymentDraft', () => {
  it(
    'builds an exact backend payment request',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId:
              sourceAccount.id,
            destinationAccountId:
              destinationAccount.id,
            amount: '10.5',
          },
          accounts,
        ),
      ).toEqual({
        ok: true,
        draft: {
          sourceAccountId:
            sourceAccount.id,
          destinationAccountId:
            destinationAccount.id,
          amountMinorUnits: 1050,
        },
      })
    },
  )

  it(
    'requires both account selections and an amount',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId: '',
            destinationAccountId: '',
            amount: '',
          },
          accounts,
        ),
      ).toEqual({
        ok: false,
        errors: {
          sourceAccountId:
            'Choose a source account.',
          destinationAccountId:
            'Choose a destination account.',
          amount:
            'Enter a payment amount.',
        },
      })
    },
  )

  it(
    'requires different source and destination accounts',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId:
              sourceAccount.id,
            destinationAccountId:
              sourceAccount.id,
            amount: '1.00',
          },
          accounts,
        ),
      ).toMatchObject({
        ok: false,
        errors: {
          destinationAccountId:
            'Choose a different destination account.',
        },
      })
    },
  )

  it(
    'rejects an inactive account',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId:
              frozenAccount.id,
            destinationAccountId:
              destinationAccount.id,
            amount: '1.00',
          },
          accounts,
        ),
      ).toMatchObject({
        ok: false,
        errors: {
          sourceAccountId:
            'Choose an active source account.',
        },
      })
    },
  )

  it(
    'rejects an amount above the source balance',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId:
              sourceAccount.id,
            destinationAccountId:
              destinationAccount.id,
            amount: '12.51',
          },
          accounts,
        ),
      ).toMatchObject({
        ok: false,
        errors: {
          amount:
            'The payment amount exceeds the source account balance.',
        },
      })
    },
  )

  it(
    'preserves exact amount validation errors',
    () => {
      expect(
        validatePaymentDraft(
          {
            sourceAccountId:
              sourceAccount.id,
            destinationAccountId:
              destinationAccount.id,
            amount: '1.234',
          },
          accounts,
        ),
      ).toMatchObject({
        ok: false,
        errors: {
          amount:
            'Enter a GBP amount using whole pounds and up to two decimal places.',
        },
      })
    },
  )
})
