import { Link } from 'react-router-dom';
import { useI18n } from '../../shared/i18n/i18n-context';

export function NotFoundPage() {
  const { t } = useI18n();
  return (
    <section className="system-page" aria-labelledby="not-found-title">
      <p className="eyebrow">404</p>
      <h1 id="not-found-title">{t('system.notFound')}</h1>
      <p>{t('system.notFoundDescription')}</p>
      <Link to="/">{t('system.returnHome')}</Link>
    </section>
  );
}
