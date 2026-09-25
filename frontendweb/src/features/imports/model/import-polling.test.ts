import {describe,expect,it} from 'vitest';
import {importPollInterval,isTerminalImportStatus} from './import-polling';
describe('import polling',()=>{it('never polls terminal 202 results',()=>{expect(isTerminalImportStatus('ACCEPTED')).toBe(true);expect(importPollInterval('ACCEPTED',true,0)).toBe(false);expect(importPollInterval('FAILED',true,0)).toBe(false)});it('pauses while hidden and is bounded',()=>{expect(importPollInterval('PROCESSING',false,0)).toBe(false);expect(importPollInterval('PROCESSING',true,20)).toBe(false);expect(importPollInterval('PROCESSING',true,4)).toBe(15000)})});
