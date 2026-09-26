import { describe, expect, it } from 'vitest';
import { campaignQueries } from './campaign.queries';
describe('campaign run polling',()=>{it('stops for every terminal run state',()=>{const options=campaignQueries.run('c','r');const interval=options.refetchInterval as (query:any)=>number|false;for(const status of ['COMPLETED','FAILED','CANCELLED'])expect(interval({state:{data:{status}}})).toBe(false);expect(interval({state:{data:{status:'RUNNING'}}})).toBe(3000);});});
