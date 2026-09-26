import { describe, expect, it } from 'vitest';
import { messageListPath } from './message.api';
describe('messageListPath',()=>{it('owns filters and slice page in URL',()=>{expect(messageListPath('campaign 1','run/2',{status:'FAILED',channel:'EMAIL',customerId:'customer-3',page:2,size:25})).toBe('/api/v1/campaigns/campaign%201/runs/run%2F2/messages?status=FAILED&channel=EMAIL&customerId=customer-3&page=2&size=25');});});
