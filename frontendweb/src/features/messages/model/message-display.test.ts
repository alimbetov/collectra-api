import { describe, expect, it } from 'vitest';
import { messageChannelLabel, messageStatusLabel, messageStatusTone } from './message-display';
describe('message display maps',()=>{it('keeps unknown backend values forward compatible',()=>{expect(messageStatusLabel('FUTURE_STATE')).toBe('FUTURE_STATE');expect(messageChannelLabel('PUSH')).toBe('PUSH');expect(messageStatusTone('FUTURE_STATE')).toBe('neutral');});});
