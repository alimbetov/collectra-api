import type { SegmentCreateCommand, SegmentOptionDto, SegmentUpdateCommand } from '../../../entities/customer/model/customer.types';

export interface SegmentForm {
  code: string;
  name: string;
  description: string;
  active: boolean;
  version: number | null;
}

export type SegmentFormErrors = Partial<Record<'code' | 'name' | 'description', string>>;

export const segmentToForm = (segment?: SegmentOptionDto): SegmentForm => segment ? {
  code: segment.code, name: segment.name, description: segment.description ?? '',
  active: segment.active, version: segment.version,
} : { code: '', name: '', description: '', active: true, version: null };

export function validateSegmentForm(form: SegmentForm, editing: boolean): SegmentFormErrors {
  const errors: SegmentFormErrors = {};
  if (!editing && !form.code.trim()) errors.code = 'required';
  if (form.code.trim().length > 80) errors.code = 'tooLong';
  if (!form.name.trim()) errors.name = 'required';
  if (form.name.trim().length > 200) errors.name = 'tooLong';
  if (form.description.trim().length > 1000) errors.description = 'tooLong';
  return errors;
}

export const segmentCreateCommand = (form: SegmentForm): SegmentCreateCommand => ({
  code: form.code.trim(), name: form.name.trim(), description: form.description.trim() || null,
});

export function segmentUpdateCommand(form: SegmentForm): SegmentUpdateCommand {
  if (form.version == null) throw new Error('Segment version is required');
  return {
    name: form.name.trim(), description: form.description.trim() || null,
    active: form.active, version: form.version,
  };
}
