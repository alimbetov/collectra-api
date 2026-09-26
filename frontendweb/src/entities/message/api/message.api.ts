import { apiRequest } from '../../../shared/api/http-client';
import type { MessageDetailDto, MessageListQuery, MessageSliceDto } from '../model/message.types';
const enc = encodeURIComponent;
export function messageListPath(campaignId: string, runId: string, query: MessageListQuery) {
  const p = new URLSearchParams();
  if (query.status) p.set('status', query.status);
  if (query.channel) p.set('channel', query.channel);
  if (query.customerId) p.set('customerId', query.customerId);
  p.set('page', String(query.page ?? 0)); p.set('size', String(query.size ?? 50));
  return `/api/v1/campaigns/${enc(campaignId)}/runs/${enc(runId)}/messages?${p}`;
}
export const getMessages = (campaignId:string, runId:string, query:MessageListQuery) => apiRequest<MessageSliceDto>(messageListPath(campaignId,runId,query));
export const getMessage = (campaignId:string, runId:string, messageId:string) => apiRequest<MessageDetailDto>(`/api/v1/campaigns/${enc(campaignId)}/runs/${enc(runId)}/messages/${enc(messageId)}`);
