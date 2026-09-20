import { describe, expect, it } from 'vitest';
import { segmentCreateCommand, segmentToForm, segmentUpdateCommand, validateSegmentForm } from './segment-form';

describe('segment form mapping', () => {
  it('normalizes create values and validates required fields', () => {
    const form = segmentToForm();
    expect(validateSegmentForm(form, false)).toMatchObject({ code: 'required', name: 'required' });
    form.code = ' vip ';
    form.name = ' Priority ';
    expect(segmentCreateCommand(form)).toEqual({ code: 'vip', name: 'Priority', description: null });
  });

  it('uses the authoritative segment version for update', () => {
    const form = segmentToForm({ id: 's', code: 'VIP', name: 'VIP', description: null, active: true, createdAt: '', updatedAt: '', version: 4 });
    form.active = false;
    expect(segmentUpdateCommand(form)).toEqual({ name: 'VIP', description: null, active: false, version: 4 });
  });
});
