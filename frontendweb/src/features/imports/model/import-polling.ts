import type {ImportStatus} from '../../../entities/import/model/import.types';
export const isTerminalImportStatus=(s:ImportStatus)=>s==='ACCEPTED'||s==='FAILED';
export function importPollInterval(status:ImportStatus|undefined,visible:boolean,attempt:number){if(!visible||!status||isTerminalImportStatus(status)||attempt>=20)return false;return Math.min(1000*2**Math.min(attempt,4),15000);}
export function newIdempotencyKey(){return crypto.randomUUID();}
