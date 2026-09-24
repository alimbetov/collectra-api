import { beforeEach, describe, expect, it, vi } from 'vitest';
import { apiRequest } from '../../../shared/api/http-client';
import {
  activateCampaign,
  campaignListPath,
  prepareCampaignRun,
  updateCampaign,
} from './campaign.api';

vi.mock('../../../shared/api/http-client', () => ({
  apiRequest: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(apiRequest).mockReset();
  vi.mocked(apiRequest).mockResolvedValue({} as never);
});

describe('campaign api', () => {
  it('serializes list filters', () => {
    expect(
      campaignListPath({
        search: 'September',
        status: 'DRAFT',
        channel: 'EMAIL',
        page: 2,
        size: 50,
        sort: 'createdAt,desc',
      }),
    ).toBe(
      '/api/v1/campaigns?search=September&status=DRAFT&channel=EMAIL&page=2&size=50&sort=createdAt%2Cdesc',
    );
  });

  it('sends optimistic revision when updating and activating', async () => {
    await updateCampaign('campaign-1', {
      name: 'Campaign',
      templateVersionId: 'version-1',
      channel: 'EMAIL',
      scheduledAt: null,
      audienceSelectionType: 'CUSTOMER',
      selection: {
        customerIds: ['customer-1'],
        segmentIds: [],
        daysOverdueFrom: null,
        daysOverdueTo: null,
        amountFrom: null,
        amountTo: null,
      },
      documentTemplateVersionId: null,
      generatedPdfLink: false,
      revision: 7,
    });
    await activateCampaign('campaign-1', 7);

    expect(apiRequest).toHaveBeenNthCalledWith(
      1,
      '/api/v1/campaigns/campaign-1',
      expect.objectContaining({
        method: 'PUT',
        body: expect.objectContaining({ revision: 7 }),
      }),
    );
    expect(apiRequest).toHaveBeenNthCalledWith(
      2,
      '/api/v1/campaigns/campaign-1/activate?revision=7',
      { method: 'POST' },
    );
  });

  it('sends stable command id for idempotent run preparation', async () => {
    await prepareCampaignRun('campaign-1', '11111111-1111-1111-1111-111111111111');

    expect(apiRequest).toHaveBeenCalledWith('/api/v1/campaigns/campaign-1/runs', {
      method: 'POST',
      headers: { 'X-Command-Id': '11111111-1111-1111-1111-111111111111' },
    });
  });
});
