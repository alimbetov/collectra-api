import { describe, expect, it, vi, beforeEach } from 'vitest';
import * as http from '../../../shared/api/http-client';
import {
  activateServiceClientCredential,
  blockServiceClient,
  createServiceClient,
  getMappingTransformations,
  getServiceClient,
  getServiceClients,
  getServiceClientScopes,
  rotateServiceClientSecret,
  unblockServiceClient,
  getIntegrationSources, getIntegrationSource, getIntegrationSourceReadiness, createIntegrationSource,
  activateIntegrationSource, suspendIntegrationSource, archiveIntegrationSource, getSourceSchemaDefinitions, getMappingProfileDefinitions,
  createSourceSchema, getSourceSchemaVersions, createSourceSchemaVersion, getSourceFields, validateSourceSchema, transitionSourceSchema,
  createMappingProfile, getMappingProfileVersions, createMappingProfileVersion, getMappingRules, validateMappingProfile, transitionMappingProfile,
  updateSourceField, deleteSourceField, addMappingRule, updateMappingRule, deleteMappingRule, getTargetFields,
} from './integration.api';

vi.mock('../../../shared/api/http-client', () => ({ apiRequest: vi.fn() }));
const request = vi.mocked(http.apiRequest);

describe('integration api contracts', () => {
  beforeEach(() => request.mockReset());

  it('uses exact service client read contracts', async () => {
    await getServiceClients();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients');
    await getServiceClient('a/b');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/a%2Fb');
    await getServiceClientScopes();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-client-scopes');
  });

  it('uses exact service client mutation contracts', async () => {
    const command = { clientId: 'erp', name: 'ERP', scopes: ['integration:imports:read'] };
    await createServiceClient(command);
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients', { method: 'POST', body: command });

    const rotate = {};
    await rotateServiceClientSecret('client', rotate);
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/rotate-secret', { method: 'POST', body: rotate });

    await activateServiceClientCredential('client', 'credential');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/credentials/credential/activate', { method: 'POST' });

    await blockServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/block', { method: 'POST' });
    await unblockServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client/unblock', { method: 'POST' });
  });

  it('never sends or expects a client secret on safe read contracts', async () => {
    await getServiceClients();
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients');
    await getServiceClient('client');
    expect(request).toHaveBeenLastCalledWith('/api/v1/integration/service-clients/client');
  });

  it('uses exact integration source contracts', async () => {
    await getIntegrationSources(); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources');
    await getIntegrationSource('a/b'); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/a%2Fb');
    await getIntegrationSourceReadiness('source'); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/readiness');
    const command={code:'erp',name:'ERP',serviceClientId:'c',sourceSchemaDefinitionId:'s',mappingProfileDefinitionId:'m'};
    await createIntegrationSource(command); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources',{method:'POST',body:command});
    await activateIntegrationSource('source',3); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/activate',{method:'POST',body:{version:3}});
    await suspendIntegrationSource('source',4); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/suspend',{method:'POST',body:{version:4}});
    await archiveIntegrationSource('source',5); expect(request).toHaveBeenLastCalledWith('/api/v1/integration/sources/source/archive',{method:'POST',body:{version:5}});
    await getSourceSchemaDefinitions(); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas');
    await getMappingProfileDefinitions(); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles');
  });

  it('uses exact schema studio contracts', async () => {
    await createSourceSchema({code:'erp',name:'ERP'}); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas',{method:'POST',body:{code:'erp',name:'ERP'}});
    await getSourceSchemaVersions('s'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/s/versions');
    await createSourceSchemaVersion('s','JSON'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/s/versions',{method:'POST',body:{format:'JSON'}});
    await getSourceFields('v'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/versions/v/fields');
    await validateSourceSchema('v'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/versions/v/validate',{method:'POST'});
    await transitionSourceSchema('v','publish'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/versions/v/publish',{method:'POST'});
    const field={sourcePath:'customer.id',detectedType:null,sampleValue:null,required:true,position:1,scope:'DOCUMENT' as const,documentKey:true,valuePolicy:'FIRST_NON_EMPTY'};
    await updateSourceField('v','f',field); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/versions/v/fields/f',{method:'PUT',body:field});
    await deleteSourceField('v','f'); expect(request).toHaveBeenLastCalledWith('/api/v1/source-schemas/versions/v/fields/f',{method:'DELETE'});
  });

  it('uses exact mapping studio contracts', async () => {
    await createMappingProfile({code:'customer',name:'Customer',documentType:'CUSTOMER'}); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles',{method:'POST',body:{code:'customer',name:'Customer',documentType:'CUSTOMER'}});
    await getMappingProfileVersions('m'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/m/versions');
    await createMappingProfileVersion('m','s'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/m/versions',{method:'POST',body:{sourceSchemaVersionId:'s'}});
    await getMappingRules('v'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/rules');
    await validateMappingProfile('v'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/validate',{method:'POST'});
    await transitionMappingProfile('v','publish'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/publish',{method:'POST'});
    const rule={sourceFieldId:'s',targetFieldId:'t',transformation:{type:'TRIM'},defaultValue:null,required:true};
    await addMappingRule('v',rule); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/rules',{method:'POST',body:rule});
    await updateMappingRule('v','r',rule); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/rules/r',{method:'PUT',body:rule});
    await deleteMappingRule('v','r'); expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-profiles/versions/v/rules/r',{method:'DELETE'});
    await getTargetFields(); expect(request).toHaveBeenLastCalledWith('/api/v1/templates/fields');
  });

  it('uses backend-owned mapping metadata', async () => {
    await getMappingTransformations();
    expect(request).toHaveBeenLastCalledWith('/api/v1/mapping-metadata/transformations');
  });
});
