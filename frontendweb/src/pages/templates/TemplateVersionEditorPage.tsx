import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, Navigate, useBlocker, useParams } from 'react-router-dom';
import {
  previewBuilderDraft,
  previewPdf,
  transitionTemplateVersion,
  updateBuilderVersion,
  validateBuilderDraft,
  validateSavedVersion,
} from '../../entities/template/api/template.api';
import { templateKeys, templateQueries } from '../../entities/template/api/template.queries';
import type {
  BuilderBlockDto,
  BuilderDocumentDto,
  BuilderVersionDto,
  TemplateAssetDto,
  TemplateChannel,
  TemplateFieldDto,
  TemplatePreviewDto,
  TemplateValidationResultDto,
} from '../../entities/template/model/template.types';
import { useAuth } from '../../features/auth/model/auth-context';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Alert, Button, ConfirmDialog, Spinner, StatusBadge } from '../../shared/ui';

const DEFAULT_SAMPLE = '{\\n  "customer": {"name":"ACME","displayName":"ACME"},\\n  "invoice": {"invoiceNumber":"INV-2026-001","outstandingAmount":125000,"currency":"KZT"},\\n  "items": [{"name":"Service A","amount":125000}]\\n}';

export function TemplateVersionEditorPage() {
  const { templateId = '', versionId = '' } = useParams();
  const { t } = useI18n();
  const { hasPermission } = useAuth();
  const canManage = hasPermission('TEMPLATE_MANAGE');
  const canPublish = hasPermission('TEMPLATE_PUBLISH');
  const client = useQueryClient();
  const [fieldSearch, setFieldSearch] = useState('');
  const [assetSearch, setAssetSearch] = useState('');

  const version = useQuery(templateQueries.version(versionId));
  const capabilities = useQuery(templateQueries.capabilities());
  const fields = useQuery(templateQueries.fields(0, 50, fieldSearch));
  const assets = useQuery(templateQueries.assets(0, 50, assetSearch));

  const [document, setDocument] = useState<BuilderDocumentDto | null>(null);
  const [subject, setSubject] = useState('');
  const [stylesheet, setStylesheet] = useState('');
  const [sample, setSample] = useState(DEFAULT_SAMPLE);
  const [validation, setValidation] = useState<TemplateValidationResultDto | null>(null);
  const [preview, setPreview] = useState<TemplatePreviewDto | null>(null);
  const [pdfUrl, setPdfUrl] = useState<string | null>(null);
  const [conflict, setConflict] = useState<{
    localRevision: number;
    serverRevision: number;
    serverUpdatedAt: string;
  } | null>(null);
  const [operationError, setOperationError] = useState<unknown>(null);
  const initializedVersionRef = useRef<string | null>(null);
  const previewSequence = useRef(0);

  useEffect(() => {
    const data = version.data;
    if (!data || initializedVersionRef.current === data.id) return;
    initializedVersionRef.current = data.id;
    setDocument(
      data.builderJson ?? {
        version: '1.0',
        blocks: [{ type: 'richText', props: { content: [{ type: 'text', value: data.content }] } }],
      },
    );
    setSubject(data.subject ?? '');
    setStylesheet(data.stylesheet ?? '');
    setConflict(null);
  }, [version.data]);

  useEffect(
    () => () => {
      if (pdfUrl) URL.revokeObjectURL(pdfUrl);
    },
    [pdfUrl],
  );

  const serverProjection = useMemo(
    () => (version.data ? projection(version.data) : null),
    [version.data],
  );
  const localProjection = useMemo(
    () => (document ? JSON.stringify({ document, subject, stylesheet }) : null),
    [document, stylesheet, subject],
  );
  const dirty = Boolean(serverProjection && localProjection && serverProjection !== localProjection);
  const readOnly = version.data?.status !== 'DRAFT' || !canManage;

  const blocker = useBlocker(({ currentLocation, nextLocation }) =>
    dirty && currentLocation.pathname !== nextLocation.pathname,
  );

  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const draftCommand = () => {
    if (!version.data || !document) throw new Error('Version is not loaded');
    return {
      channel: version.data.channel,
      locale: version.data.locale,
      subject: version.data.channel === 'EMAIL' ? subject : null,
      builderJson: document,
      stylesheet: isText(version.data.channel) ? null : stylesheet || null,
      revision: version.data.revision,
    };
  };

  const saveMutation = useMutation({
    mutationFn: () => updateBuilderVersion(versionId, draftCommand()),
    onSuccess: async (saved) => {
      client.setQueryData(templateKeys.version(versionId), saved);
      initializedVersionRef.current = null;
      setConflict(null);
      setValidation(null);
      setOperationError(null);
      await Promise.all([
        client.invalidateQueries({ queryKey: templateKeys.versionLists(templateId) }),
        client.invalidateQueries({ queryKey: templateKeys.lists() }),
      ]);
    },
    onError: async (error) => {
      setOperationError(error);
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        const localRevision = version.data?.revision ?? 0;
        const refreshed = await version.refetch();
        setConflict({
          localRevision,
          serverRevision: refreshed.data?.revision ?? localRevision,
          serverUpdatedAt: refreshed.data?.updatedAt ?? version.data?.updatedAt ?? '',
        });
      }
    },
  });

  const draftValidationMutation = useMutation({
    mutationFn: () => validateBuilderDraft(draftCommand()),
    onSuccess: (result) => {
      setValidation(result);
      setOperationError(null);
    },
    onError: setOperationError,
  });

  const savedValidationMutation = useMutation({
    mutationFn: () => {
      if (!version.data) throw new Error('Version is not loaded');
      return validateSavedVersion(versionId, version.data.revision);
    },
    onSuccess: async (result) => {
      setValidation(result);
      await version.refetch();
      await client.invalidateQueries({ queryKey: templateKeys.versionLists(templateId) });
    },
    onError: async (error) => {
      setOperationError(error);
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        const localRevision = version.data?.revision ?? 0;
        const refreshed = await version.refetch();
        setConflict({
          localRevision,
          serverRevision: refreshed.data?.revision ?? localRevision,
          serverUpdatedAt: refreshed.data?.updatedAt ?? version.data?.updatedAt ?? '',
        });
      }
    },
  });

  const previewMutation = useMutation({
    mutationFn: async () => {
      const requestId = ++previewSequence.current;
      const payload = JSON.parse(sample) as unknown;
      const current = version.data;
      if (!current) throw new Error('Version not loaded');

      if (current.channel === 'PDF') {
        const blob = await previewPdf(draftCommand(), payload);
        return { requestId, kind: 'pdf' as const, blob };
      }

      const value = await previewBuilderDraft(draftCommand(), payload);
      return { requestId, kind: 'content' as const, value };
    },
    onSuccess: (result) => {
      if (result.requestId !== previewSequence.current) return;
      setOperationError(null);
      if (result.kind === 'pdf') {
        if (pdfUrl) URL.revokeObjectURL(pdfUrl);
        setPdfUrl(URL.createObjectURL(result.blob));
        setPreview(null);
      } else {
        setPreview(result.value);
        if (pdfUrl) URL.revokeObjectURL(pdfUrl);
        setPdfUrl(null);
      }
    },
    onError: setOperationError,
  });

  const transitionMutation = useMutation({
    mutationFn: (action: 'publish' | 'reopen' | 'archive') => {
      if (!version.data) throw new Error('Version not loaded');
      return transitionTemplateVersion(versionId, action, version.data.revision);
    },
    onSuccess: async (updated) => {
      client.setQueryData(templateKeys.version(versionId), updated);
      initializedVersionRef.current = null;
      setConflict(null);
      await Promise.all([
        client.invalidateQueries({ queryKey: templateKeys.versionLists(templateId) }),
        client.invalidateQueries({ queryKey: templateKeys.lists() }),
        client.invalidateQueries({ queryKey: templateKeys.options(updated.channel) }),
        client.invalidateQueries({ queryKey: templateKeys.versions(updated.templateId, updated.channel) }),
      ]);
    },
    onError: async (error) => {
      setOperationError(error);
      if (error instanceof ApiError && error.problem?.code === 'VERSION_CONFLICT') {
        const localRevision = version.data?.revision ?? 0;
        const refreshed = await version.refetch();
        setConflict({
          localRevision,
          serverRevision: refreshed.data?.revision ?? localRevision,
          serverUpdatedAt: refreshed.data?.updatedAt ?? version.data?.updatedAt ?? '',
        });
      }
    },
  });

  if (version.error instanceof ApiError && version.error.status === 403) {
    return <Navigate to="/forbidden" replace />;
  }
  if (version.isLoading) return <Spinner label={t('templates.editorLoading')} />;
  if (version.error || !version.data || !document) {
    return <ProblemDetailPanel error={version.error} onRetry={() => void version.refetch()} />;
  }

  const data = version.data;
  const supportedSchema =
    !capabilities.data || capabilities.data.builderSchemaVersion === document.version;

  if (!supportedSchema) {
    return (
      <div className="customer-detail">
        <Link to={'/templates/' + templateId}>← {t('templates.back')}</Link>
        <Alert variant="danger">{t('templates.unsupportedSchema')}</Alert>
      </div>
    );
  }

  const addBlock = (type: BuilderBlockDto['type']) => {
    if (readOnly) return;
    setValidation(null);
    setDocument((current) =>
      current ? { ...current, blocks: [...current.blocks, newBlock(type, data.channel)] } : current,
    );
  };

  const removeBlock = (index: number) => {
    if (readOnly) return;
    setValidation(null);
    setDocument((current) =>
      current ? { ...current, blocks: current.blocks.filter((_, i) => i !== index) } : current,
    );
  };

  const moveBlock = (index: number, delta: -1 | 1) => {
    if (readOnly) return;
    setDocument((current) => {
      if (!current) return current;
      const target = index + delta;
      if (target < 0 || target >= current.blocks.length) return current;
      const blocks = [...current.blocks];
      const selected = blocks[index];
      blocks[index] = blocks[target];
      blocks[target] = selected;
      return { ...current, blocks };
    });
  };

  const insertPlaceholder = (field: TemplateFieldDto) => {
    if (readOnly) return;
    setDocument((current) => current && appendPlaceholder(current, field.key));
  };

  const insertAsset = (asset: TemplateAssetDto) => {
    if (readOnly || isText(data.channel)) return;
    setDocument((current) =>
      current
        ? {
            ...current,
            blocks: [...current.blocks, { type: 'image', props: { assetKey: asset.key, alt: asset.altText ?? '' } }],
          }
        : current,
    );
  };

  return (
    <div className="template-editor">
      <header className="customer-detail__header">
        <div>
          <Link className="customer-detail__back" to={'/templates/' + templateId}>
            ← {t('templates.backToTemplate')}
          </Link>
          <p className="eyebrow">{t('templates.editor')}</p>
          <h1>{'v' + data.templateVersion + ' · ' + data.channel + ' · ' + data.locale}</h1>
          <div className="campaign-detail__badges">
            <StatusBadge tone={data.status === 'PUBLISHED' ? 'success' : 'warning'}>{data.status}</StatusBadge>
            {dirty ? <StatusBadge tone="warning">{t('templates.unsaved')}</StatusBadge> : null}
            <span className="muted-text">{'revision ' + data.revision}</span>
          </div>
        </div>
        <div className="campaign-detail__actions">
          {data.status === 'DRAFT' && canManage ? (
            <>
              <Button variant="secondary" loading={draftValidationMutation.isPending} onClick={() => draftValidationMutation.mutate()}>
                {t('templates.checkDraft')}
              </Button>
              <Button variant="secondary" loading={previewMutation.isPending} onClick={() => previewMutation.mutate()}>
                {t('templates.preview')}
              </Button>
              <Button loading={saveMutation.isPending} disabled={!dirty || Boolean(conflict)} onClick={() => saveMutation.mutate()}>
                {t('templates.save')}
              </Button>
              <Button variant="secondary" loading={savedValidationMutation.isPending} disabled={dirty} onClick={() => savedValidationMutation.mutate()}>
                {t('templates.validateVersion')}
              </Button>
            </>
          ) : null}
          {data.status === 'VALIDATED' && canPublish ? (
            <>
              <Button onClick={() => transitionMutation.mutate('publish')}>{t('templates.publish')}</Button>
              <Button variant="secondary" onClick={() => transitionMutation.mutate('reopen')}>{t('templates.reopen')}</Button>
            </>
          ) : null}
          {data.status === 'PUBLISHED' && canPublish ? (
            <Button variant="danger" onClick={() => transitionMutation.mutate('archive')}>{t('templates.archiveVersion')}</Button>
          ) : null}
        </div>
      </header>

      {conflict ? (
        <Alert variant="danger">
          <strong>{t('templates.conflictTitle')}</strong>
          <p>{t('templates.conflictDescription')}</p>
          <p className="muted-text">
            {t('templates.conflictMeta')
              .replace('{local}', String(conflict.localRevision))
              .replace('{server}', String(conflict.serverRevision))
              .replace('{updatedAt}', conflict.serverUpdatedAt)}
          </p>
          <div className="campaign-form__actions">
            <Button
              variant="secondary"
              onClick={() => {
                initializedVersionRef.current = null;
                if (version.data) {
                  setDocument(version.data.builderJson);
                  setSubject(version.data.subject ?? '');
                  setStylesheet(version.data.stylesheet ?? '');
                }
                setConflict(null);
              }}
            >
              {t('templates.reloadServer')}
            </Button>
            <Button variant="secondary" onClick={() => setConflict(null)}>{t('templates.keepDraft')}</Button>
          </div>
        </Alert>
      ) : null}

      {operationError ? <ProblemDetailPanel error={operationError} /> : null}

      <div className="template-editor__layout">
        <aside className="customer-detail-card">
          <h2>{t('templates.blocks')}</h2>
          <div className="campaign-form__actions">
            {(capabilities.data?.blockSupport[data.channel] ?? []).map((type) => (
              <Button key={type} type="button" variant="secondary" disabled={readOnly} onClick={() => addBlock(type)}>
                {'+ ' + type}
              </Button>
            ))}
          </div>

          <h2>{t('templates.variables')}</h2>
          <input
            value={fieldSearch}
            placeholder={t('templates.catalogSearch')}
            onChange={(event) => setFieldSearch(event.target.value)}
          />
          {fields.isLoading ? <Spinner label={t('templates.loadingFields')} /> : null}
          {fields.data?.items.map((field) => (
            <button
              key={field.id}
              type="button"
              className="table-link template-editor__catalog-item"
              disabled={readOnly}
              title={field.description ?? field.exampleValue ?? field.key}
              onClick={() => insertPlaceholder(field)}
            >
              <strong>{field.label}</strong>
              <code>{field.key}</code>
            </button>
          ))}

          {!isText(data.channel) ? (
            <>
              <h2>{t('templates.assets')}</h2>
              <input
                value={assetSearch}
                placeholder={t('templates.catalogSearch')}
                onChange={(event) => setAssetSearch(event.target.value)}
              />
              {assets.data?.items.map((asset) => (
                <button
                  key={asset.id}
                  type="button"
                  className="table-link template-editor__catalog-item"
                  disabled={readOnly}
                  onClick={() => insertAsset(asset)}
                >
                  {asset.key}
                </button>
              ))}
            </>
          ) : null}
        </aside>

        <main className="customer-detail-card">
          {data.channel === 'EMAIL' ? (
            <label className="ui-form-field">
              <span className="ui-form-field__label">{t('templates.subject')}</span>
              <input
                value={subject}
                maxLength={300}
                disabled={readOnly}
                onChange={(event) => {
                  setSubject(event.target.value);
                  setValidation(null);
                }}
              />
            </label>
          ) : null}

          <h2>{t('templates.document')}</h2>
          <div className="template-editor__blocks">
            {document.blocks.map((block, index) => (
              <BlockEditor
                key={index}
                block={block}
                index={index}
                readOnly={readOnly}
                onChange={(next) => {
                  setValidation(null);
                  setDocument((current) =>
                    current
                      ? { ...current, blocks: current.blocks.map((item, i) => (i === index ? next : item)) }
                      : current,
                  );
                }}
                onRemove={() => removeBlock(index)}
                onUp={() => moveBlock(index, -1)}
                onDown={() => moveBlock(index, 1)}
              />
            ))}
          </div>

          {!isText(data.channel) ? (
            <label className="ui-form-field">
              <span className="ui-form-field__label">{t('templates.stylesheet')}</span>
              <textarea
                rows={8}
                value={stylesheet}
                disabled={readOnly}
                onChange={(event) => {
                  setStylesheet(event.target.value);
                  setValidation(null);
                }}
              />
            </label>
          ) : null}
        </main>

        <aside className="customer-detail-card">
          <h2>{t('templates.samplePayload')}</h2>
          <textarea rows={16} value={sample} onChange={(event) => setSample(event.target.value)} />

          <h2>{t('templates.diagnostics')}</h2>
          {validation ? (
            validation.valid ? (
              <Alert variant="success">{t('templates.validationOk')}</Alert>
            ) : (
              <Alert variant="danger">
                <ul>
                  {validation.errors.map((issue) => (
                    <li key={issue.code + ':' + issue.path + ':' + issue.message}>
                      <strong>{issue.code}</strong> {'[' + issue.path + '] ' + issue.message}
                    </li>
                  ))}
                </ul>
              </Alert>
            )
          ) : (
            <p className="muted-text">{t('templates.noValidation')}</p>
          )}

          <h2>{t('templates.preview')}</h2>
          {pdfUrl ? (
            <iframe title={t('templates.pdfPreview')} src={pdfUrl} className="template-editor__preview-frame" />
          ) : null}
          {preview && data.channel === 'EMAIL' ? (
            <>
              <strong>{preview.subject}</strong>
              <iframe title={t('templates.emailPreview')} sandbox="" srcDoc={preview.content} className="template-editor__preview-frame" />
            </>
          ) : null}
          {preview && isText(data.channel) ? (
            <pre className="template-editor__text-preview">{preview.content}</pre>
          ) : null}
        </aside>
      </div>
      <ConfirmDialog
        open={blocker.state === 'blocked'}
        title={t('templates.leaveTitle')}
        confirmLabel={t('templates.leaveConfirm')}
        cancelLabel={t('templates.cancel')}
        closeLabel={t('templates.close')}
        destructive
        onCancel={() => blocker.reset?.()}
        onConfirm={() => blocker.proceed?.()}
      >
        <p>{t('templates.leaveDescription')}</p>
      </ConfirmDialog>
    </div>
  );
}

function BlockEditor({
  block,
  index,
  readOnly,
  onChange,
  onRemove,
  onUp,
  onDown,
}: {
  block: BuilderBlockDto;
  index: number;
  readOnly: boolean;
  onChange: (value: BuilderBlockDto) => void;
  onRemove: () => void;
  onUp: () => void;
  onDown: () => void;
}) {
  const { t } = useI18n();

  return (
    <section className="template-editor__block">
      <header>
        <strong>{String(index + 1) + '. ' + block.type}</strong>
        <div className="campaign-form__actions">
          <button type="button" disabled={readOnly || index === 0} onClick={onUp} aria-label={t('templates.moveUp')}>↑</button>
          <button type="button" disabled={readOnly} onClick={onDown} aria-label={t('templates.moveDown')}>↓</button>
          <button type="button" disabled={readOnly} onClick={onRemove}>{t('templates.remove')}</button>
        </div>
      </header>

      {block.type === 'richText' ? (
        <textarea
          rows={5}
          disabled={readOnly}
          value={richTextToSource(block)}
          onChange={(event) => onChange(sourceToRichText(event.target.value))}
        />
      ) : null}

      {block.type === 'spacer' ? (
        <input
          type="number"
          min={0}
          max={500}
          disabled={readOnly}
          value={block.props.heightPx}
          onChange={(event) =>
            onChange({
              type: 'spacer',
              props: { heightPx: Math.max(0, Math.min(500, Number(event.target.value) || 0)) },
            })
          }
        />
      ) : null}

      {block.type === 'image' ? <code>{block.props.assetKey}</code> : null}
      {block.type === 'itemsTable' ? <code>{block.props.columns.map((column) => column.key).join(', ')}</code> : null}
    </section>
  );
}

function projection(value: BuilderVersionDto): string {
  return JSON.stringify({
    document: value.builderJson,
    subject: value.subject ?? '',
    stylesheet: value.stylesheet ?? '',
  });
}

function isText(channel: TemplateChannel) {
  return channel === 'SMS' || channel === 'WHATSAPP' || channel === 'TELEGRAM';
}

function newBlock(type: BuilderBlockDto['type'], channel: TemplateChannel): BuilderBlockDto {
  if (type === 'richText') return { type, props: { content: [{ type: 'text', value: '' }] } };
  if (type === 'itemsTable') {
    return { type, props: { dataSource: 'items', columns: [{ key: 'name', label: 'Name' }] } };
  }
  if (type === 'image') {
    if (isText(channel)) throw new Error('Image is not supported for text channels');
    return { type, props: { assetKey: 'logo', alt: '' } };
  }
  if (type === 'spacer') return { type, props: { heightPx: 16 } };
  return { type, children: [] };
}

function appendPlaceholder(document: BuilderDocumentDto, key: string): BuilderDocumentDto {
  const blocks = [...document.blocks];
  const index = blocks.findIndex((block) => block.type === 'richText');
  const node = { type: 'placeholder' as const, key };
  if (index < 0) {
    blocks.push({ type: 'richText', props: { content: [node] } });
  } else {
    const block = blocks[index];
    if (block.type === 'richText') {
      blocks[index] = { ...block, props: { content: [...block.props.content, node] } };
    }
  }
  return { ...document, blocks };
}

function richTextToSource(block: Extract<BuilderBlockDto, { type: 'richText' }>): string {
  return block.props.content
    .map((node) => (node.type === 'text' ? node.value : '{{' + node.key + '}}'))
    .join('');
}

function sourceToRichText(value: string): Extract<BuilderBlockDto, { type: 'richText' }> {
  const content: Extract<BuilderBlockDto, { type: 'richText' }>['props']['content'] = [];
  const pattern = /\{\{([a-z][A-Za-z0-9_]*(?:\.[a-z][A-Za-z0-9_]*)+)\}\}/g;
  let cursor = 0;
  for (const match of value.matchAll(pattern)) {
    const index = match.index ?? 0;
    if (index > cursor) content.push({ type: 'text', value: value.slice(cursor, index) });
    content.push({ type: 'placeholder', key: match[1] });
    cursor = index + match[0].length;
  }
  if (cursor < value.length) content.push({ type: 'text', value: value.slice(cursor) });
  if (!content.length) content.push({ type: 'text', value });
  return { type: 'richText', props: { content } };
}
