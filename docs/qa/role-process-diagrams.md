# Collectra — Role Process Diagrams

Status: REVIEW BASELINE

## Platform Super Admin
```mermaid
flowchart TD
 L[Platform login] --> O[Overview]
 O --> T[Tenants]
 T --> TD[Tenant detail]
 O --> U[Users]
 U --> UD[User detail]
 O --> A[Platform administrators]
 TD --> X{Tenant-scoped business data?}
 X -->|No implicit access| O
```

## Tenant Administrator
```mermaid
flowchart TD
 L[Tenant login] --> M[Members]
 M --> I[Invite]
 M --> R[Roles and permissions]
 R --> AR[Assign roles]
 AR --> V[Re-authorize on next request]
 V --> C[Integration configuration]
```

## Integration/Data Manager + Technical Client
```mermaid
flowchart LR
 SC[Service Client] --> SS[Source Schema]
 SS --> MP[Mapping Profile]
 MP --> IS[IntegrationSource]
 IS --> RD[Readiness]
 RD -->|READY| AC[Activate]
 AC --> AUTH[Service authentication]
 AUTH --> ING[Idempotent ingest]
 ING --> OP[Durable operation]
 OP --> REC[Record diagnostics]
 REC --> B[Customer / Invoice / Payment]
```

## Operator / Receivables
```mermaid
flowchart LR
 D[Dashboard] --> C[Customer]
 C --> CT[Contacts/Segments]
 C --> CO[Contract optional]
 C --> I[Invoice]
 C --> P[Payment]
 P --> A[Allocation]
 I --> A
 A --> R[Authoritative balances]
 I --> CC[Collection entry]
```

## Collection Officer
```mermaid
stateDiagram-v2
 [*] --> OPEN
 OPEN --> IN_PROGRESS: start
 IN_PROGRESS --> ON_HOLD: hold
 ON_HOLD --> IN_PROGRESS: resume
 IN_PROGRESS --> CLOSED: close
 state IN_PROGRESS {
   [*] --> Work
   Work --> Promise
   Work --> Dispute
   Work --> Action
 }
```

## Content + Campaign Manager
```mermaid
flowchart LR
 T[Template] --> V[Version]
 V --> VA[Validate]
 VA --> PR[Preview]
 PR --> PU[Publish]
 PU --> C[Campaign]
 C --> AU[Audience]
 AU --> AC[Activate]
 AC --> RUN[Prepare/Run]
 RUN --> M[Messages]
 M --> ATT[Attachment gate]
 ATT --> W[Delivery worker/router]
 W --> CH[Mock channel boundary]
 CH --> MON[Message monitoring]
```

## Support/Ops
```mermaid
flowchart TD
 R[Run/Message] --> S[Safe status]
 S --> E[Normalized error]
 S --> A[Attempt count]
 S --> P[Provider message id]
 S --> AT[Attachment readiness]
 E --> N[No raw body / destination / credentials]
```

## Auditor / Read-only
```mermaid
flowchart TD
 L[Login] --> R[Granted read surfaces]
 R --> F[Filter/Page/Inspect]
 F --> M{Mutation attempted}
 M -->|Yes| D[403 and no state change]
 M -->|No| R
```
