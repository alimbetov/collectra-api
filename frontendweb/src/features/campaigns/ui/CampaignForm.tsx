import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { customerQueries } from '../../../entities/customer/api/customer.queries';
import { templateQueries } from '../../../entities/template/api/template.queries';
import type {
  AudienceSelectionType,
  CampaignChannel,
  CampaignDetailDto,
  CampaignSaveCommand,
} from '../../../entities/campaign/model/campaign.types';
import { Button, FormField } from '../../../shared/ui';
import { useI18n } from '../../../shared/i18n/i18n-context';

interface CampaignFormProps {
  initial?: CampaignDetailDto;
  pending?: boolean;
  submitLabel: string;
  onSubmit: (command: CampaignSaveCommand) => void;
}

const channels: CampaignChannel[] = ['EMAIL', 'SMS', 'TELEGRAM', 'WHATSAPP'];

function toLocalDateTime(value: string | null | undefined): string {
  if (!value) return '';
  const date = new Date(value);
  if (Number.isNaN(date.valueOf())) return '';
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}

function toInstant(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.valueOf()) ? null : date.toISOString();
}

function numbers(value: string): number | null {
  if (!value.trim()) return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

export function CampaignForm({
  initial,
  pending = false,
  submitLabel,
  onSubmit,
}: CampaignFormProps) {
  const { t } = useI18n();
  const [name, setName] = useState(initial?.name ?? '');
  const [channel, setChannel] = useState<CampaignChannel>(initial?.channel ?? 'EMAIL');
  const [audienceType, setAudienceType] = useState<AudienceSelectionType>(
    initial?.audienceSelectionType ?? 'CUSTOMER',
  );
  const [messageTemplateId, setMessageTemplateId] = useState('');
  const [messageTemplateVersionId, setMessageTemplateVersionId] = useState(
    initial?.messageTemplateVersionId ?? '',
  );
  const [documentTemplateId, setDocumentTemplateId] = useState('');
  const [documentTemplateVersionId, setDocumentTemplateVersionId] = useState(
    initial?.documentTemplateVersionId ?? '',
  );
  const [generatedPdfLink, setGeneratedPdfLink] = useState(initial?.generatedPdfLink ?? false);
  const [scheduledAt, setScheduledAt] = useState(toLocalDateTime(initial?.scheduledAt));
  const [customerIds, setCustomerIds] = useState<string[]>(initial?.selection.customerIds ?? []);
  const [segmentIds, setSegmentIds] = useState<string[]>(initial?.selection.segmentIds ?? []);
  const [daysOverdueFrom, setDaysOverdueFrom] = useState(
    initial?.selection.daysOverdueFrom?.toString() ?? '',
  );
  const [daysOverdueTo, setDaysOverdueTo] = useState(
    initial?.selection.daysOverdueTo?.toString() ?? '',
  );
  const [amountFrom, setAmountFrom] = useState(initial?.selection.amountFrom ?? '');
  const [amountTo, setAmountTo] = useState(initial?.selection.amountTo ?? '');

  useEffect(() => {
    if (!initial) return;
    setName(initial.name);
    setChannel(initial.channel);
    setAudienceType(initial.audienceSelectionType);
    setMessageTemplateVersionId(initial.messageTemplateVersionId);
    setDocumentTemplateVersionId(initial.documentTemplateVersionId ?? '');
    setGeneratedPdfLink(initial.generatedPdfLink);
    setScheduledAt(toLocalDateTime(initial.scheduledAt));
    setCustomerIds(initial.selection.customerIds ?? []);
    setSegmentIds(initial.selection.segmentIds ?? []);
    setDaysOverdueFrom(initial.selection.daysOverdueFrom?.toString() ?? '');
    setDaysOverdueTo(initial.selection.daysOverdueTo?.toString() ?? '');
    setAmountFrom(initial.selection.amountFrom ?? '');
    setAmountTo(initial.selection.amountTo ?? '');
  }, [initial]);

  const customers = useQuery(
    customerQueries.list({
      status: 'ACTIVE',
      page: 0,
      size: 50,
      sort: 'displayName,asc',
    }),
  );
  const segments = useQuery(
    customerQueries.segmentList({
      active: true,
      page: 0,
      size: 100,
      sort: 'name,asc',
    }),
  );
  const messageTemplates = useQuery(templateQueries.options(channel));
  const messageVersions = useQuery(templateQueries.versions(messageTemplateId, channel));
  const documentTemplates = useQuery(templateQueries.options('PDF'));
  const documentVersions = useQuery(templateQueries.versions(documentTemplateId, 'PDF'));

  const selectedCustomerOptions = useMemo(
    () => new Set(customers.data?.items.map((item) => item.id) ?? []),
    [customers.data?.items],
  );
  const selectedSegmentOptions = useMemo(
    () => new Set(segments.data?.items.map((item) => item.id) ?? []),
    [segments.data?.items],
  );

  const submit = (event: React.FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !messageTemplateVersionId) return;
    onSubmit({
      name: name.trim(),
      templateVersionId: messageTemplateVersionId,
      channel,
      scheduledAt: toInstant(scheduledAt),
      audienceSelectionType: audienceType,
      selection: {
        customerIds,
        segmentIds,
        daysOverdueFrom: numbers(daysOverdueFrom),
        daysOverdueTo: numbers(daysOverdueTo),
        amountFrom: amountFrom.trim() || null,
        amountTo: amountTo.trim() || null,
      },
      documentTemplateVersionId: generatedPdfLink
        ? documentTemplateVersionId || null
        : null,
      generatedPdfLink,
    });
  };

  return (
    <form className="campaign-form" onSubmit={submit}>
      <div className="campaign-form__grid">
        <FormField label={t('campaigns.name')} required>
          <input value={name} maxLength={200} onChange={(event) => setName(event.target.value)} />
        </FormField>

        <FormField label={t('campaigns.channel')} required>
          <select
            value={channel}
            onChange={(event) => {
              setChannel(event.target.value as CampaignChannel);
              setMessageTemplateId('');
              setMessageTemplateVersionId('');
            }}
          >
            {channels.map((value) => (
              <option key={value} value={value}>
                {value}
              </option>
            ))}
          </select>
        </FormField>

        <FormField label={t('campaigns.messageTemplate')} required>
          <select
            value={messageTemplateId}
            onChange={(event) => {
              setMessageTemplateId(event.target.value);
              setMessageTemplateVersionId('');
            }}
          >
            <option value="">—</option>
            {messageTemplates.data?.items.map((template) => (
              <option key={template.id} value={template.id}>
                {template.name} · {template.code}
              </option>
            ))}
          </select>
        </FormField>

        <FormField label={t('campaigns.templateVersion')} required>
          <select
            value={messageTemplateVersionId}
            onChange={(event) => setMessageTemplateVersionId(event.target.value)}
          >
            {initial?.messageTemplateVersionId && !messageTemplateId ? (
              <option value={initial.messageTemplateVersionId}>
                Current · {initial.messageTemplateVersionId}
              </option>
            ) : (
              <option value="">—</option>
            )}
            {messageVersions.data?.items.map((version) => (
              <option key={version.id} value={version.id}>
                v{version.templateVersion} · {version.locale} · {version.channel}
              </option>
            ))}
          </select>
        </FormField>

        <FormField label={t('campaigns.audienceType')}>
          <select
            value={audienceType}
            onChange={(event) => setAudienceType(event.target.value as AudienceSelectionType)}
          >
            <option value="CUSTOMER">{t('campaigns.customer')}</option>
            <option value="RECEIVABLE">{t('campaigns.receivable')}</option>
          </select>
        </FormField>

        <FormField label={t('campaigns.scheduledAt')}>
          <input
            type="datetime-local"
            value={scheduledAt}
            onChange={(event) => setScheduledAt(event.target.value)}
          />
        </FormField>
      </div>

      <section className="campaign-form__section">
        <h3>{t('campaigns.audience')}</h3>
        <div className="campaign-form__grid">
          <FormField label={t('campaigns.customer')}>
            <select
              multiple
              size={6}
              value={customerIds}
              onChange={(event) =>
                setCustomerIds(Array.from(event.currentTarget.selectedOptions, (option) => option.value))
              }
            >
              {customerIds
                .filter((id) => !selectedCustomerOptions.has(id))
                .map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              {customers.data?.items.map((customer) => (
                <option key={customer.id} value={customer.id}>
                  {customer.displayName} · {customer.externalId}
                </option>
              ))}
            </select>
          </FormField>

          <FormField label={t('campaigns.segments')}>
            <select
              multiple
              size={6}
              value={segmentIds}
              onChange={(event) =>
                setSegmentIds(Array.from(event.currentTarget.selectedOptions, (option) => option.value))
              }
            >
              {segmentIds
                .filter((id) => !selectedSegmentOptions.has(id))
                .map((id) => (
                  <option key={id} value={id}>
                    {id}
                  </option>
                ))}
              {segments.data?.items.map((segment) => (
                <option key={segment.id} value={segment.id}>
                  {segment.name} · {segment.code}
                </option>
              ))}
            </select>
          </FormField>

          {audienceType === 'RECEIVABLE' ? (
            <>
              <FormField label={t('campaigns.daysFrom')}>
                <input
                  type="number"
                  min={0}
                  value={daysOverdueFrom}
                  onChange={(event) => setDaysOverdueFrom(event.target.value)}
                />
              </FormField>
              <FormField label={t('campaigns.daysTo')}>
                <input
                  type="number"
                  min={0}
                  value={daysOverdueTo}
                  onChange={(event) => setDaysOverdueTo(event.target.value)}
                />
              </FormField>
              <FormField label={t('campaigns.amountFrom')}>
                <input
                  inputMode="decimal"
                  value={amountFrom}
                  onChange={(event) => setAmountFrom(event.target.value)}
                />
              </FormField>
              <FormField label={t('campaigns.amountTo')}>
                <input
                  inputMode="decimal"
                  value={amountTo}
                  onChange={(event) => setAmountTo(event.target.value)}
                />
              </FormField>
            </>
          ) : null}
        </div>
      </section>

      <section className="campaign-form__section">
        <label className="campaign-form__check">
          <input
            type="checkbox"
            checked={generatedPdfLink}
            onChange={(event) => setGeneratedPdfLink(event.target.checked)}
          />
          <span>{t('campaigns.generatedPdfLink')}</span>
        </label>

        {generatedPdfLink ? (
          <div className="campaign-form__grid">
            <FormField label={t('campaigns.documentTemplate')}>
              <select
                value={documentTemplateId}
                onChange={(event) => {
                  setDocumentTemplateId(event.target.value);
                  setDocumentTemplateVersionId('');
                }}
              >
                <option value="">—</option>
                {documentTemplates.data?.items.map((template) => (
                  <option key={template.id} value={template.id}>
                    {template.name} · {template.code}
                  </option>
                ))}
              </select>
            </FormField>
            <FormField label={t('campaigns.templateVersion')}>
              <select
                value={documentTemplateVersionId}
                onChange={(event) => setDocumentTemplateVersionId(event.target.value)}
              >
                {initial?.documentTemplateVersionId && !documentTemplateId ? (
                  <option value={initial.documentTemplateVersionId}>
                    Current · {initial.documentTemplateVersionId}
                  </option>
                ) : (
                  <option value="">—</option>
                )}
                {documentVersions.data?.items.map((version) => (
                  <option key={version.id} value={version.id}>
                    v{version.templateVersion} · {version.locale}
                  </option>
                ))}
              </select>
            </FormField>
          </div>
        ) : null}
      </section>

      <div className="campaign-form__actions">
        <Button type="submit" loading={pending} disabled={!name.trim() || !messageTemplateVersionId}>
          {submitLabel}
        </Button>
      </div>
    </form>
  );
}
