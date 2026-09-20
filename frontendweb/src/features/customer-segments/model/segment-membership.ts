export interface MembershipDelta {
  additions: string[];
  removals: string[];
}

export function membershipDelta(original: readonly string[], selected: ReadonlySet<string>): MembershipDelta {
  const originalSet = new Set(original);
  return {
    additions: [...selected].filter((id) => !originalSet.has(id)).sort(),
    removals: original.filter((id) => !selected.has(id)).sort(),
  };
}
