import { isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { ApiError } from '../../shared/api/http-client';
import { ProblemDetailPanel } from '../../shared/errors/ProblemDetailPanel';

export function RouteErrorPage() {
  const routeError = useRouteError();
  const error = isRouteErrorResponse(routeError)
    ? new ApiError(routeError.status, { status: routeError.status })
    : routeError;
  return <main className="global-error"><ProblemDetailPanel error={error} onRetry={() => window.location.reload()} /></main>;
}
