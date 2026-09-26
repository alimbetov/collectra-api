import { describe, expect, it } from 'vitest';
import { messageKeys } from './message.queries';
describe('message query keys',()=>{it('isolate campaign run and message hierarchy',()=>{expect(messageKeys.detail('c1','r1','m1')).not.toEqual(messageKeys.detail('c1','r2','m1'));expect(messageKeys.detail('c1','r1','m1')).not.toEqual(messageKeys.detail('c2','r1','m1'));});});
