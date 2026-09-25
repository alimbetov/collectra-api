import { queryOptions } from '@tanstack/react-query';
import {
  getMappingTransformations,
  getServiceClient,
  getServiceClients,
  getServiceClientScopes,
  getIntegrationSources, getIntegrationSource, getIntegrationSourceReadiness, getSourceSchemaDefinitions, getMappingProfileDefinitions,
} from './integration.api';

export const integrationKeys = {
  all: ['integration'] as const,
  serviceClients: () => [...integrationKeys.all, 'service-clients'] as const,
  serviceClient: (id: string) => [...integrationKeys.serviceClients(), id] as const,
  scopes: () => [...integrationKeys.all, 'service-client-scopes'] as const,
  transformations: () => [...integrationKeys.all, 'mapping-transformations'] as const,
  sources: () => [...integrationKeys.all, 'sources'] as const,
  source: (id: string) => [...integrationKeys.sources(), id] as const,
  readiness: (id: string) => [...integrationKeys.source(id), 'readiness'] as const,
  sourceSchemas: () => [...integrationKeys.all, 'source-schemas'] as const,
  mappingProfiles: () => [...integrationKeys.all, 'mapping-profiles'] as const,
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
  sources: () => queryOptions({ queryKey: integrationKeys.sources(), queryFn: getIntegrationSources }),
  source: (id: string) => queryOptions({ queryKey: integrationKeys.source(id), queryFn: () => getIntegrationSource(id), enabled: Boolean(id) }),
  readiness: (id: string) => queryOptions({ queryKey: integrationKeys.readiness(id), queryFn: () => getIntegrationSourceReadiness(id), enabled: Boolean(id) }),
  sourceSchemas: () => queryOptions({ queryKey: integrationKeys.sourceSchemas(), queryFn: getSourceSchemaDefinitions }),
  mappingProfiles: () => queryOptions({ queryKey: integrationKeys.mappingProfiles(), queryFn: getMappingProfileDefinitions }),
  transformations: () => queryOptions({
    queryKey: integrationKeys.transformations(),
    queryFn: getMappingTransformations,
    staleTime: 30 * 60 * 1000,
  }),
};
