import {
  HttpResponse,
  http,
} from 'msw'
import {
  beforeEach,
  describe,
  expect,
  it,
} from 'vitest'

import { ApiContractError } from '../../../shared/api/apiClient'
import { clearCsrfToken } from '../../../shared/api/csrfToken'
import { server } from '../../../test/server'
import { getSettlementDiscrepancy } from './getSettlementDiscrepancy'
import { resolveSettlementDiscrepancy } from './resolveSettlementDiscrepancy'
import type {
  SettlementDiscrepancy,
  SettlementImport,
} from './settlement'
import { uploadSettlementFile } from './uploadSettlementFile'

const csrfEndpoint =
  'http://localhost:5173/api/v1/identity/csrf'
const importEndpoint =
  'http://localhost:5173/api/v1/settlement-imports'
const discrepancyId =
  '11111111-1111-4111-8111-111111111111'
const discrepancyEndpoint =
  `http://localhost:5173/api/v1/settlement-discrepancies/${discrepancyId}`

const settlementImport: SettlementImport = {
  importId:
    '22222222-2222-4222-8222-222222222222',
  existingImport: false,
  status: 'COMPLETED',
  originalFilename: 'settlement.csv',
  rawFileSha256: 'a'.repeat(64),
  rawFileSizeBytes: 180,
  rowCount: 2,
  matchedCount: 1,
  discrepancyCount: 1,
  createdAt: '2026-07-24T10:00:00Z',
  completedAt: '2026-07-24T10:00:01Z',
}

const discrepancy:
  SettlementDiscrepancy = {
    discrepancyId,
    importId: settlementImport.importId,
    rowNumber: 2,
    settlementRecordId: 'settlement-2',
    paymentId:
      '33333333-3333-4333-8333-333333333333',
    amountMinorUnits: 2500,
    currency: 'GBP',
    settledAt: '2026-07-24T10:00:00Z',
    code: 'AMOUNT_MISMATCH',
    status: 'OPEN',
    createdAt: '2026-07-24T10:00:01Z',
    version: 0,
    resolution: null,
  }

beforeEach(() => {
  clearCsrfToken()
})

describe('settlement analyst API', () => {
  it(
    'uploads multipart CSV with CSRF evidence',
    async () => {
      let contentType: string | null = null
      let csrf: string | null = null
      let bodyText = ''

      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'settlement-csrf-token',
          })
        }),

        http.post(
          importEndpoint,
          async ({ request }) => {
            contentType =
              request.headers.get(
                'content-type',
              )
            csrf = request.headers.get(
              'X-CSRF-TOKEN',
            )
            bodyText = await request.text()

            return HttpResponse.json(
              settlementImport,
              {
                status: 201,
              },
            )
          },
        ),
      )

      const file = new File(
        ['header\r\nrow\r\n'],
        'settlement.csv',
        {
          type: 'text/csv',
        },
      )

      await expect(
        uploadSettlementFile(file),
      ).resolves.toEqual(settlementImport)

      expect(contentType).toMatch(
        /^multipart\/form-data; boundary=/,
      )
      expect(csrf).toBe(
        'settlement-csrf-token',
      )
      expect(bodyText).toContain(
        'name="file"',
      )
      expect(bodyText).toContain(
        'Content-Type: text/csv',
      )
    },
  )

  it(
    'requires a strong ETag matching the discrepancy version',
    async () => {
      server.use(
        http.get(
          discrepancyEndpoint,
          () => {
            return HttpResponse.json(
              discrepancy,
              {
                headers: {
                  ETag: '"0"',
                },
              },
            )
          },
        ),
      )

      await expect(
        getSettlementDiscrepancy(
          discrepancyId,
        ),
      ).resolves.toEqual({
        discrepancy,
        etag: '"0"',
      })

      server.use(
        http.get(
          discrepancyEndpoint,
          () => {
            return HttpResponse.json(
              discrepancy,
              {
                headers: {
                  ETag: 'W/"0"',
                },
              },
            )
          },
        ),
      )

      await expect(
        getSettlementDiscrepancy(
          discrepancyId,
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )

  it(
    'resolves with CSRF, If-Match and a body-only decision',
    async () => {
      let csrf: string | null = null
      let ifMatch: string | null = null
      let body: unknown
      const resolved: SettlementDiscrepancy = {
        ...discrepancy,
        status: 'RESOLVED',
        version: 1,
        resolution: {
          resolutionId:
            '44444444-4444-4444-8444-444444444444',
          actorIdentityUserId:
            '55555555-5555-4555-8555-555555555555',
          decision:
            'INTERNAL_CORRECTION_REQUIRED',
          reason:
            'Investigate the internal posting.',
          discrepancyVersion: 0,
          decidedAt:
            '2026-07-24T10:05:00Z',
        },
      }

      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'resolution-csrf-token',
          })
        }),

        http.put(
          `${discrepancyEndpoint}/resolution`,
          async ({ request }) => {
            csrf = request.headers.get(
              'X-CSRF-TOKEN',
            )
            ifMatch =
              request.headers.get('If-Match')
            body = await request.json()

            return HttpResponse.json(resolved)
          },
        ),
      )

      await expect(
        resolveSettlementDiscrepancy({
          discrepancyId,
          etag: '"0"',
          decision:
            'INTERNAL_CORRECTION_REQUIRED',
          reason:
            'Investigate the internal posting.',
        }),
      ).resolves.toEqual(resolved)

      expect(csrf).toBe(
        'resolution-csrf-token',
      )
      expect(ifMatch).toBe('"0"')
      expect(body).toEqual({
        decision:
          'INTERNAL_CORRECTION_REQUIRED',
        reason:
          'Investigate the internal posting.',
      })
    },
  )

  it(
    'rejects incoherent import counts',
    async () => {
      server.use(
        http.get(csrfEndpoint, () => {
          return HttpResponse.json({
            headerName: 'X-CSRF-TOKEN',
            parameterName: '_csrf',
            token: 'settlement-csrf-token',
          })
        }),

        http.post(importEndpoint, () => {
          return HttpResponse.json(
            {
              ...settlementImport,
              matchedCount: 2,
            },
            {
              status: 201,
            },
          )
        }),
      )

      await expect(
        uploadSettlementFile(
          new File(
            ['header\r\nrow\r\n'],
            'settlement.csv',
          ),
        ),
      ).rejects.toBeInstanceOf(
        ApiContractError,
      )
    },
  )
})
