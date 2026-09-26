import { queryOptions } from '@tanstack/react-query';
import { getMessage, getMessages } from './message.api';
import type { MessageListQuery } from '../model/message.types';
export const messageKeys={all:['messages'] as const,run:(campaignId:string,runId:string)=>['messages',campaignId,runId] as const,list:(campaignId:string,runId:string,q:MessageListQuery)=>['messages',campaignId,runId,'list',q] as const,detail:(campaignId:string,runId:string,messageId:string)=>['messages',campaignId,runId,'detail',messageId] as const};
export const messageQueries={list:(campaignId:string,runId:string,q:MessageListQuery)=>queryOptions({queryKey:messageKeys.list(campaignId,runId,q),queryFn:()=>getMessages(campaignId,runId,q),enabled:Boolean(campaignId&&runId)}),detail:(campaignId:string,runId:string,messageId:string)=>queryOptions({queryKey:messageKeys.detail(campaignId,runId,messageId),queryFn:()=>getMessage(campaignId,runId,messageId),enabled:Boolean(campaignId&&runId&&messageId)})};
