import { useI18n } from '../../shared/i18n/i18n-context';

export function PlatformPlaceholderPage({ titleKey }: { titleKey: 'platform.navigation.tenants' | 'platform.navigation.users' | 'platform.navigation.administrators' | 'platform.navigation.analytics' | 'platform.navigation.audit' | 'platform.navigation.operations' }) {
  const { t } = useI18n();
  return (
    <div className="placeholder-page">
      <p className="eyebrow">{t('platform.eyebrow')}</p>
      <h1>{t(titleKey)}</h1>
      <p>{t('platform.sectionPlanned')}</p>
    </div>
  );
}
