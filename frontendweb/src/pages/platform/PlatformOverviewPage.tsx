import { useQuery, useQueryClient } from '@tanstack/react-query';
import { platformKeys, platformQueries } from '../../entities/platform/api/platform.queries';
import { useI18n } from '../../shared/i18n/i18n-context';
import { Button } from '../../shared/ui';

const number = new Intl.NumberFormat();

export function PlatformOverviewPage() {
  const { t } = useI18n();
  const queryClient = useQueryClient();
  const overview = useQuery(platformQueries.overview());

  return (
    <div className="platform-overview">
      <header className="dashboard-page__header">
        <div>
          <p className="eyebrow">{t('platform.eyebrow')}</p>
          <h1>{t('platform.overview.title')}</h1>
          <p className="platform-overview__subtitle">{t('platform.overview.description')}</p>
        </div>
        <Button
          variant="secondary"
          loading={overview.isFetching}
          onClick={() => void queryClient.invalidateQueries({ queryKey: platformKeys.overview() })}
        >
          {t('dashboard.refresh')}
        </Button>
      </header>

      {overview.isLoading ? (
        <div className="platform-overview__state">{t('dashboard.loading')}</div>
      ) : overview.error ? (
        <div className="ui-alert ui-alert--danger" role="alert">
          <strong>{t('error.title')}</strong>
          <span>{t('error.server')}</span>
        </div>
      ) : overview.data ? (
        <>
          <div className="platform-kpi-grid">
            <Kpi label={t('platform.overview.tenants')} value={overview.data.tenantsTotal} />
            <Kpi label={t('platform.overview.activeTenants')} value={overview.data.tenantsActive} />
            <Kpi label={t('platform.overview.users')} value={overview.data.usersTotal} />
            <Kpi label={t('platform.overview.activeUsers')} value={overview.data.usersActive} />
            <Kpi label={t('platform.overview.messages30d')} value={overview.data.messagesLast30Days} />
            <Kpi label={t('platform.overview.sent30d')} value={overview.data.sentLast30Days} />
            <Kpi label={t('platform.overview.failed30d')} value={overview.data.failedLast30Days} />
            <Kpi label={t('platform.overview.unknown')} value={overview.data.unknownCurrent} />
          </div>

          <section className="platform-overview__section">
            <div>
              <h2>{t('platform.overview.channels')}</h2>
              <p>{t('platform.overview.period30d')}</p>
            </div>
            {overview.data.channels.length === 0 ? (
              <div className="ui-empty-state">{t('dashboard.emptyDelivery')}</div>
            ) : (
              <div className="ui-data-table-scroll">
                <table className="ui-data-table">
                  <thead>
                    <tr>
                      <th>{t('platform.overview.channel')}</th>
                      <th data-align="end">{t('platform.overview.messages')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {overview.data.channels.map((channel) => (
                      <tr key={channel.channel}>
                        <td>{channel.channel}</td>
                        <td data-align="end">{number.format(channel.messages)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <p className="platform-overview__freshness">
            {t('platform.overview.generatedAt')}: {new Date(overview.data.generatedAt).toLocaleString()}
          </p>
        </>
      ) : null}
    </div>
  );
}

function Kpi({ label, value }: { label: string; value: number }) {
  return (
    <article className="platform-kpi">
      <span>{label}</span>
      <strong>{number.format(value)}</strong>
    </article>
  );
}
