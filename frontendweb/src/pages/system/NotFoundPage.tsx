import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section className="system-page" aria-labelledby="not-found-title">
      <p className="eyebrow">404</p>
      <h1 id="not-found-title">Page not found</h1>
      <p>The requested workspace page does not exist.</p>
      <Link to="/">Return to dashboard</Link>
    </section>
  );
}
