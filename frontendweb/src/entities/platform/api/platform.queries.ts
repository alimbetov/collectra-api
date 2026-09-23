import { queryOptions } from '@tanstack/react-query';
import { getPlatformOverview } from './platform.api';

export const platformKeys = {
  all: ['platform'] as const,
  overview: () => [...platformKeys.all, 'overview'] as const,
};

export const platformQueries = {
  overview: () =>
    queryOptions({
      queryKey: platformKeys.overview(),
      queryFn: getPlatformOverview,
    }),
};
