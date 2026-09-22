import { useAuth } from '../../features/auth/model/auth-context';
import { useI18n } from '../../shared/i18n/i18n-context';

export function PlatformPage() {
  const { logout, user } = useAuth();
  const { t } = useI18n();

  return (
    <main className="platform-page">
      <section className="platform-panel" aria-labelledby="platform-title">
        <div className="brand">Collectra</div>
        <p className="eyebrow">{t('platform.eyebrow')}</p>
        <h1 id="platform-title">{t('platform.title')}</h1>
        <p className="platform-description">{t('platform.description')}</p>

        <dl className="platform-facts">
          <div>
            <dt>{t('platform.account')}</dt>
            <dd>{user?.email ?? 'super-admin'}</dd>
          </div>
          <div>
            <dt>{t('platform.role')}</dt>
            <dd>PLATFORM_SUPER_ADMIN</dd>
          </div>
          <div>
            <dt>{t('platform.api')}</dt>
            <dd>/api/v1/platform/auth/login</dd>
          </div>
        </dl>

        <button type="button" className="secondary-button" onClick={() => void logout()}>
          {t('shell.signOut')}
        </button>
      </section>
    </main>
  );
}
