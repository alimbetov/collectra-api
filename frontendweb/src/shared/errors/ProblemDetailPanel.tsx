import { useState } from 'react';
import { useI18n } from '../i18n/i18n-context';
import { Alert, Button } from '../ui';
import { toProblemViewModel } from './problem-detail';

interface ProblemDetailPanelProps {
  error: unknown;
  onRetry?: () => void;
}

export function ProblemDetailPanel({ error, onRetry }: ProblemDetailPanelProps) {
  const { t } = useI18n();
  const [copied, setCopied] = useState(false);
  const problem = toProblemViewModel(error);
  const description = problem.detail ?? (problem.status !== null && problem.status >= 500 ? t('error.server') : t('error.unexpected'));

  async function copySupportId() {
    if (!problem.supportId || !navigator.clipboard) return;
    try {
      await navigator.clipboard.writeText(problem.supportId);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  }

  return (
    <Alert variant="danger" title={t('error.title')}>
      <p>{description}</p>
      {problem.supportId ? (
        <div className="problem-support-id">
          <span>{t('error.supportId')}: <code>{problem.supportId}</code></span>
          <Button variant="secondary" type="button" onClick={() => void copySupportId()}>
            {copied ? t('error.copied') : t('error.copyId')}
          </Button>
        </div>
      ) : null}
      {onRetry ? <Button type="button" onClick={onRetry}>{t('error.retry')}</Button> : null}
    </Alert>
  );
}
