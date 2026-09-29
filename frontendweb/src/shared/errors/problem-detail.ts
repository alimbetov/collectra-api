import type { ProblemDetailDto } from '../api/contracts';
import { ApiError } from '../api/http-client';

const SAFE_SUPPORT_ID = /^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$/;

export type RecoveryAction =
  | 'RELOAD'
  | 'RETRY_SAME_INTENT'
  | 'EDIT_INPUT'
  | 'OPEN_EXISTING'
  | 'CHOOSE_COMPATIBLE_RESOURCE'
  | 'CONTACT_ADMIN'
  | 'CONTACT_SUPPORT'
  | 'NONE';

export interface ProblemViewModel {
  status: number | null;
  code: string | null;
  detail: string | null;
  supportId: string | null;
  recoveryAction: RecoveryAction;
  recoveryHint: string | null;
}

const RECOVERY_BY_CODE: Record<string, Pick<ProblemViewModel, 'recoveryAction' | 'recoveryHint'>> = {
  VERSION_CONFLICT: { recoveryAction: 'RELOAD', recoveryHint: 'Данные изменились после открытия страницы. Загрузите актуальное состояние и проверьте изменения перед повтором.' },
  IDEMPOTENCY_CONFLICT: { recoveryAction: 'EDIT_INPUT', recoveryHint: 'Параметры операции изменились. Проверьте данные и отправьте новое действие вместо слепого повтора.' },
  ALLOCATION_EXCEEDS_PAYMENT: { recoveryAction: 'RELOAD', recoveryHint: 'Доступный остаток платежа изменился. Обновите данные платежа и скорректируйте сумму.' },
  ALLOCATION_EXCEEDS_INVOICE: { recoveryAction: 'RELOAD', recoveryHint: 'Остаток задолженности изменился. Обновите счёт и скорректируйте сумму.' },
  CURRENCY_MISMATCH: { recoveryAction: 'CHOOSE_COMPATIBLE_RESOURCE', recoveryHint: 'Платёж и счёт должны иметь одинаковую валюту. Выберите совместимый счёт.' },
  CUSTOMER_MISMATCH: { recoveryAction: 'CHOOSE_COMPATIBLE_RESOURCE', recoveryHint: 'Платёж и счёт должны относиться к одному клиенту. Выберите ресурс этого клиента.' },
  COLLECTION_CASE_ALREADY_ACTIVE: { recoveryAction: 'OPEN_EXISTING', recoveryHint: 'Для этой задолженности уже есть активное дело. Откройте существующее дело вместо создания нового.' },
};

export function safeSupportId(problem: ProblemDetailDto | null | undefined): string | null {
  const candidate = problem?.correlationId ?? problem?.traceId;
  return candidate && SAFE_SUPPORT_ID.test(candidate) ? candidate : null;
}

export function toProblemViewModel(error: unknown): ProblemViewModel {
  if (!(error instanceof ApiError)) {
    return { status: null, code: null, detail: null, supportId: null, recoveryAction: 'NONE', recoveryHint: null };
  }

  const problem = error.problem;
  const status = problem?.status ?? error.status;
  const code = typeof problem?.code === 'string' ? problem.code : null;
  const knownRecovery = code ? RECOVERY_BY_CODE[code] : undefined;
  const fallbackRecovery =
    status === 403
      ? { recoveryAction: 'CONTACT_ADMIN' as const, recoveryHint: 'У вашей учётной записи нет права на это действие. Обратитесь к администратору рабочего пространства.' }
      : status >= 500
        ? { recoveryAction: 'CONTACT_SUPPORT' as const, recoveryHint: 'Если проблема повторяется, передайте службе поддержки код поддержки и время операции.' }
        : { recoveryAction: 'NONE' as const, recoveryHint: null };
  return {
    status,
    code,
    // Backend validation/conflict details are actionable. Never expose opaque 5xx details.
    detail: status >= 400 && status < 500 ? problem?.detail ?? problem?.title ?? null : null,
    supportId: safeSupportId(problem),
    ...(knownRecovery ?? fallbackRecovery),
  };
}
