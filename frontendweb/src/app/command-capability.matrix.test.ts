import { describe, expect, it } from 'vitest';
import { canExecuteCommand, commandCapabilities } from './command-capability.matrix';
import { roleJourneyPersonas } from './role-journey.matrix';

function persona(id: string) {
  const value = roleJourneyPersonas.find((candidate) => candidate.id === id);
  if (!value) throw new Error(`Unknown persona: ${id}`);
  return value;
}

describe('command-level role journeys', () => {
  it.each(['payment.create', 'payment.allocate', 'payment.reverseAllocation'])(
    'receivables manager can execute %s while read-only auditor cannot',
    (command) => {
      expect(canExecuteCommand(persona('receivables-manager').permissions, command)).toBe(true);
      expect(canExecuteCommand(persona('auditor').permissions, command)).toBe(false);
    },
  );

  it.each(['collection.manageCase', 'collection.managePromise', 'collection.manageDispute', 'collection.manageAction'])(
    'collection operator can execute %s while auditor cannot',
    (command) => {
      expect(canExecuteCommand(persona('collection-operator').permissions, command)).toBe(true);
      expect(canExecuteCommand(persona('auditor').permissions, command)).toBe(false);
    },
  );

  it('campaign manager can mutate campaigns but cannot mutate tenant integrations or RBAC', () => {
    const permissions = persona('campaign-manager').permissions;
    expect(canExecuteCommand(permissions, 'campaign.manage')).toBe(true);
    expect(canExecuteCommand(permissions, 'integration.manageSource')).toBe(false);
    expect(canExecuteCommand(permissions, 'rbac.assignRole')).toBe(false);
  });

  it('template manager can edit templates but publish remains a distinct capability', () => {
    const permissions = persona('template-manager').permissions;
    expect(canExecuteCommand(permissions, 'template.manage')).toBe(true);
    expect(canExecuteCommand(permissions, 'template.publish')).toBe(false);
  });

  it('integration admin can manage sources/create clients without receiving secret rotation or RBAC implicitly', () => {
    const permissions = persona('integration-admin').permissions;
    expect(canExecuteCommand(permissions, 'integration.manageSource')).toBe(true);
    expect(canExecuteCommand(permissions, 'integration.createServiceClient')).toBe(true);
    expect(canExecuteCommand(permissions, 'integration.rotateServiceSecret')).toBe(false);
    expect(canExecuteCommand(permissions, 'rbac.createRole')).toBe(false);
  });

  it('minimal user cannot execute any registered mutation command', () => {
    const permissions = persona('minimal-user').permissions;
    for (const command of commandCapabilities) {
      expect(canExecuteCommand(permissions, command.id), command.id).toBe(false);
    }
  });

  it('every command carries explicit backend authorization evidence', () => {
    for (const command of commandCapabilities) {
      expect(command.permission).not.toHaveLength(0);
      expect(command.backendEvidence).not.toHaveLength(0);
    }
  });
});
