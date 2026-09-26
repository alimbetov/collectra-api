import { apiRequest } from '../../../shared/api/http-client';
import type { CollectionActionDto, CollectionCaseDto, CollectionCasePageDto, CollectionCloseReason, CollectionListQuery, DisputeDto, HistoryPageDto, PromiseDto, TimelineItemDto } from '../model/collection.types';

const base = '/api/v1/collection-cases';
function listPath(q: CollectionListQuery) {
  const p = new URLSearchParams();
  Object.entries(q).forEach(([k,v]) => { if (v !== undefined && v !== '') p.set(k, String(v)); });
  return `${base}?${p.toString()}`;
}
export const getCollectionCases=(q:CollectionListQuery)=>apiRequest<CollectionCasePageDto>(listPath(q));
export const getCollectionCase=(id:string)=>apiRequest<CollectionCaseDto>(`${base}/${encodeURIComponent(id)}`);
export const updateCollectionCase=(id:string,body:{version:number;priority?:string;assignedTo?:string|null})=>apiRequest<CollectionCaseDto>(`${base}/${encodeURIComponent(id)}`,{method:'PUT',body});
export const transitionCollectionCase=(id:string,action:'start'|'hold',version:number)=>apiRequest<CollectionCaseDto>(`${base}/${encodeURIComponent(id)}/${action}`,{method:'POST',body:{version}});
export const closeCollectionCase=(id:string,version:number,reason:CollectionCloseReason)=>apiRequest<CollectionCaseDto>(`${base}/${encodeURIComponent(id)}/close`,{method:'POST',body:{version,reason}});
export const getPromises=(id:string,page=0)=>apiRequest<HistoryPageDto<PromiseDto>>(`${base}/${encodeURIComponent(id)}/promises?page=${page}&size=50`);
export const createPromise=(id:string,body:{amount:string;currency:string;promisedDate:string})=>apiRequest<PromiseDto>(`${base}/${encodeURIComponent(id)}/promises`,{method:'POST',body});
export const transitionPromise=(caseId:string,promiseId:string,action:'fulfill'|'break'|'cancel',version:number)=>apiRequest<PromiseDto>(`${base}/${caseId}/promises/${promiseId}/${action}`,{method:'POST',body:{version}});
export const getDisputes=(id:string,page=0)=>apiRequest<HistoryPageDto<DisputeDto>>(`${base}/${encodeURIComponent(id)}/disputes?page=${page}&size=50`);
export const createDispute=(id:string,body:{reason:string;description?:string})=>apiRequest<DisputeDto>(`${base}/${encodeURIComponent(id)}/disputes`,{method:'POST',body});
export const resolveDispute=(caseId:string,disputeId:string,body:{version:number;resolutionCode:string;summary?:string})=>apiRequest<DisputeDto>(`${base}/${caseId}/disputes/${disputeId}/resolve`,{method:'POST',body});
export const cancelDispute=(caseId:string,disputeId:string,version:number)=>apiRequest<DisputeDto>(`${base}/${caseId}/disputes/${disputeId}/cancel`,{method:'POST',body:{version}});
export const getActions=(id:string,page=0)=>apiRequest<HistoryPageDto<CollectionActionDto>>(`${base}/${encodeURIComponent(id)}/actions?page=${page}&size=50`);
export const createAction=(id:string,body:{actionType:string;description?:string;dueAt:string;priority?:string})=>apiRequest<CollectionActionDto>(`${base}/${encodeURIComponent(id)}/actions`,{method:'POST',body});
export const transitionAction=(caseId:string,actionId:string,command:'complete'|'cancel',version:number)=>apiRequest<CollectionActionDto>(`${base}/${caseId}/actions/${actionId}/${command}`,{method:'POST',body:{version}});
export const getTimeline=(id:string,page=0)=>apiRequest<HistoryPageDto<TimelineItemDto>>(`${base}/${encodeURIComponent(id)}/timeline?page=${page}&size=50`);
