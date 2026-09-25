import {apiRequest} from '../../../shared/api/http-client';
import type {ImportCreateCommand,ImportCreateResultDto,ImportDetailDto,ImportDiagnosticDto,ImportListParams,PageDto} from '../model/import.types';
const enc=encodeURIComponent;
export function getImports(p:ImportListParams){const q=new URLSearchParams({page:String(p.page),size:String(p.size)});if(p.status)q.set('status',p.status);if(p.sort)q.set('sort',p.sort);return apiRequest<PageDto<ImportDetailDto>>('/api/v1/import-batches?'+q);}
export const getImport=(id:string)=>apiRequest<ImportDetailDto>(`/api/v1/import-batches/${enc(id)}/operations`);
export const getImportErrors=(id:string,page:number,size=50)=>apiRequest<PageDto<ImportDiagnosticDto>>(`/api/v1/import-batches/${enc(id)}/errors?page=${page}&size=${size}`);
export function createImport(c:ImportCreateCommand){const q=new URLSearchParams({mappingProfileVersionId:c.mappingProfileVersionId,templateVersionId:c.templateVersionId,formats:c.formats.join(',')});const body=new FormData();body.set('file',c.file);return apiRequest<ImportCreateResultDto>('/api/v1/import-batches?'+q,{method:'POST',headers:{'Idempotency-Key':c.idempotencyKey},body});}
