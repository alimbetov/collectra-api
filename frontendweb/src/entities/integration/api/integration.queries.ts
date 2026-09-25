import { queryOptions } from '@tanstack/react-query';
import {
  getMappingTransformations,
  getServiceClient,
  getServiceClients,
  getServiceClientScopes,
} from './integration.api';

export const integrationKeys = {
  all: ['integration'] as const,
  serviceClients: () => [...integrationKeys.all, 'service-clients'] as const,
  serviceClient: (id: string) => [...integrationKeys.serviceClients(), id] as const,
  scopes: () => [...integrationKeys.all, 'service-client-scopes'] as const,
  transformations: () => [...integrationKeys.all, 'mapping-transformations'] as const,
};

export const integrationQueries = {
  serviceClients: () => queryOptions({
    queryKey: integrationKeys.serviceClients(),
    queryFn: getServiceClients,
  }),
  serviceClient: (id: string) => queryOptions({
    queryKey: integrationKeys.serviceClient(id),
    queryFn: () => getServiceClient(id),
    enabled: Boolean(id),
  }),
  scopes: () => queryOptions({
    queryKey: integrationKeys.scopes(),
    queryFn: getServiceClientScopes,
    staleTime: 30 * 60 * 1000,
  }),
  transformations: () => queryOptions({
    queryKey: integrationKeys.transformations(),
    queryFn: getMappingTransformations,
    staleTime: 30 * 60 * 1000,
  }),
};
