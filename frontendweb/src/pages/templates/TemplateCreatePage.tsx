import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { createTemplate } from '../../entities/template/api/template.api';
import { templateKeys } from '../../entities/template/api/template.queries';
import { useAuth } from '../../features/auth/model/auth-context';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button, FormField } from '../../shared/ui';

export function TemplateCreatePage() {
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const navigate = useNavigate();
  const client = useQueryClient();
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [documentType, setDocumentType] = useState('NOTIFICATION');

  const mutation = useMutation({
    mutationFn: () =>
      createTemplate({
        code: code.trim(),
        name: name.trim(),
        documentType: documentType.trim(),
      }),
    onSuccess: async (created) => {
      await client.invalidateQueries({ queryKey: templateKeys.lists() });
      navigate(`/templates/${created.id}`, { replace: true });
    },
  });

  if (!hasPermission('TEMPLATE_MANAGE')) {
    return <Navigate to="/forbidden" replace />;
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!code.trim() || !name.trim() || !documentType.trim() || mutation.isPending) return;
    mutation.mutate();
  };

  return (
    <div className="customer-detail">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to="/templates">
            ← {t('templates.back')}
          </Link>
          <p className="eyebrow">{t('templates.eyebrow')}</p>
          <h1>{t('templates.create')}</h1>
          <p>{t('templates.createDescription')}</p>
        </div>
      </header>

      {mutation.error ? <ProblemDetailPanel error={mutation.error} /> : null}

      <form className="customer-detail-card campaign-form" onSubmit={submit}>
        <FormField label={t('templates.code')}>
          <input
            value={code}
            maxLength={100}
            autoComplete="off"
            onChange={(event) => setCode(event.target.value)}
          />
        </FormField>
        <FormField label={t('templates.name')}>
          <input
            value={name}
            maxLength={200}
            onChange={(event) => setName(event.target.value)}
          />
        </FormField>
        <FormField label={t('templates.documentType')}>
          <input
            value={documentType}
            maxLength={50}
            onChange={(event) => setDocumentType(event.target.value)}
          />
        </FormField>
        <div className="campaign-form__actions">
          <Button type="submit" loading={mutation.isPending}>
            {t('templates.create')}
          </Button>
          <Link className="ui-button ui-button--secondary" to="/templates">
            {t('templates.cancel')}
          </Link>
        </div>
      </form>
    </div>
  );
}
