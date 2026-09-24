import { queryOptions } from '@tanstack/react-query';
import type { CampaignListQuery } from '../model/campaign.types';
import {
  getCampaign,
  getCampaignRecipients,
  getCampaignRun,
  getCampaignRuns,
  getCampaigns,
} from './campaign.api';

export const campaignKeys = {
  all: ['campaigns'] as const,
  lists: () => [...campaignKeys.all, 'list'] as const,
  list: (query: CampaignListQuery) => [...campaignKeys.lists(), query] as const,
  detail: (campaignId: string) => [...campaignKeys.all, 'detail', campaignId] as const,
  runs: (campaignId: string, page: number) =>
    [...campaignKeys.detail(campaignId), 'runs', page] as const,
  run: (campaignId: string, runId: string) =>
    [...campaignKeys.detail(campaignId), 'run', runId] as const,
  recipients: (campaignId: string, runId: string, page: number) =>
    [...campaignKeys.run(campaignId, runId), 'recipients', page] as const,
};

export const campaignQueries = {
  list: (query: CampaignListQuery) =>
    queryOptions({ queryKey: campaignKeys.list(query), queryFn: () => getCampaigns(query) }),
  detail: (campaignId: string) =>
    queryOptions({
      queryKey: campaignKeys.detail(campaignId),
      queryFn: () => getCampaign(campaignId),
      enabled: Boolean(campaignId),
    }),
  runs: (campaignId: string, page = 0) =>
    queryOptions({
      queryKey: campaignKeys.runs(campaignId, page),
      queryFn: () => getCampaignRuns(campaignId, page),
      enabled: Boolean(campaignId),
    }),
  run: (campaignId: string, runId: string) =>
    queryOptions({
      queryKey: campaignKeys.run(campaignId, runId),
      queryFn: () => getCampaignRun(campaignId, runId),
      enabled: Boolean(campaignId && runId),
    }),
  recipients: (campaignId: string, runId: string, page = 0) =>
    queryOptions({
      queryKey: campaignKeys.recipients(campaignId, runId, page),
      queryFn: () => getCampaignRecipients(campaignId, runId, page),
      enabled: Boolean(campaignId && runId),
    }),
};
