import { apiRequest } from '../../../shared/api/http-client';
import type {
  CampaignAttachmentCommand,
  CampaignAttachmentDto,
  CampaignDetailDto,
  CampaignListQuery,
  CampaignPageDto,
  CampaignPreviewDto,
  CampaignRecipientPageDto,
  CampaignRunDto,
  CampaignRunPageDto,
  CampaignSaveCommand,
  CampaignUpdateCommand,
  CampaignValidationDto,
  PrepareRunDto,
} from '../model/campaign.types';

function append(params: URLSearchParams, key: string, value: string | number | undefined) {
  if (value !== undefined && value !== '') params.set(key, String(value));
}

export function campaignListPath(query: CampaignListQuery): string {
  const params = new URLSearchParams();
  append(params, 'search', query.search);
  append(params, 'status', query.status);
  append(params, 'channel', query.channel);
  append(params, 'page', query.page ?? 0);
  append(params, 'size', query.size ?? 50);
  append(params, 'sort', query.sort ?? 'createdAt,desc');
  return `/api/v1/campaigns?${params}`;
}

export const getCampaigns = (query: CampaignListQuery) =>
  apiRequest<CampaignPageDto>(campaignListPath(query));

export const getCampaign = (campaignId: string) =>
  apiRequest<CampaignDetailDto>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}`);

export const createCampaign = (command: CampaignSaveCommand) =>
  apiRequest<CampaignDetailDto>('/api/v1/campaigns', { method: 'POST', body: command });

export const updateCampaign = (campaignId: string, command: CampaignUpdateCommand) =>
  apiRequest<CampaignDetailDto>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}`, {
    method: 'PUT',
    body: command,
  });

export const validateCampaign = (campaignId: string) =>
  apiRequest<CampaignValidationDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/validate`,
    { method: 'POST' },
  );

export const previewCampaign = (campaignId: string) =>
  apiRequest<CampaignPreviewDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/preview`,
    { method: 'POST' },
  );

export const activateCampaign = (campaignId: string, revision: number) =>
  apiRequest<CampaignDetailDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/activate?revision=${revision}`,
    { method: 'POST' },
  );

export const prepareCampaignRun = (campaignId: string, commandId: string) =>
  apiRequest<PrepareRunDto>(`/api/v1/campaigns/${encodeURIComponent(campaignId)}/runs`, {
    method: 'POST',
    headers: { 'X-Command-Id': commandId },
  });

export const getCampaignRuns = (campaignId: string, page = 0) =>
  apiRequest<CampaignRunPageDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/runs?page=${page}&size=20`,
  );

export const getCampaignRun = (campaignId: string, runId: string) =>
  apiRequest<CampaignRunDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/runs/${encodeURIComponent(runId)}`,
  );

export const getCampaignRecipients = (campaignId: string, runId: string, page = 0) =>
  apiRequest<CampaignRecipientPageDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/runs/${encodeURIComponent(runId)}/recipients?page=${page}&size=50`,
  );


export const configureCampaignPdfAttachment = (
  campaignId: string,
  command: CampaignAttachmentCommand,
) =>
  apiRequest<CampaignAttachmentDto>(
    `/api/v1/campaigns/${encodeURIComponent(campaignId)}/generated-pdf-attachment`,
    { method: 'PUT', body: command },
  );
