export type ImportStatus='PROCESSING'|'ACCEPTED'|'FAILED';
export interface PageDto<T>{content:T[];number:number;size:number;totalElements:number;totalPages:number}
export interface ImportSummaryDto{id:string;source:string;status:ImportStatus;idempotencyKey:string;receivedAt:string;processingStartedAt:string|null;completedAt:string|null;recordCount:number;createdCount:number;reusedCount:number;conflictCount:number;failedCount:number}
export interface ImportDetailDto extends ImportSummaryDto{rawSourceFileId:string|null;sourceSchemaVersionId:string|null;mappingProfileVersionId:string;templateVersionId:string;processingAttempts:number;errorCode:string|null;errorMessage:string|null}
export interface ImportDocumentDto{order:number;documentKey:string;generationJobId:string}
export interface ImportCreateResultDto{batchId:string;status:ImportStatus;documentCount:number;replayed:boolean;documents:ImportDocumentDto[];failure:{code:string;message:string;failedAt:string}|null}
export interface ImportDiagnosticDto{id:string;recordNumber:number;recordOrder:number;documentKey:string|null;stage:string;fieldPath:string|null;errorCode:string;safeDetail:string;maskedSourceValue:string|null;createdAt:string}
export interface ImportListParams{status?:ImportStatus;page:number;size:number;sort?:string}
export interface ImportCreateCommand{mappingProfileVersionId:string;templateVersionId:string;formats:string[];file:File;idempotencyKey:string}
