# Top 30 Smoke Contracts

Status: DESIGN COMPLETE / EXECUTION REQUIRED
Results: PASS | PASS | FAIL | BLOCKED_DECISION(D16+) | BLOCKED_IMPLEMENTATION | WAIVED(reason)

| Smoke | Weak zone | Fixture / action | Expected business + security state | UI/API evidence | Result |
|---|---|---|---|---|---|
| T30-01 | WZ-01 | Alpha token accesses Beta IDs across domains | 404/non-disclosure; zero Beta mutation | API | PASS |
| T30-02 | WZ-02 | Alpha create/update body references Beta customer/template/file/source | reject; no cross-tenant FK/state | API+DB | PASS |
| T30-03 | WZ-03 | restricted persona calls core read/manage APIs directly | accepted D01 capability matrix enforced; denied mutation has zero state change | API+UI nav | PASS |
| T30-04 | WZ-04 | human→service, service→human, tenant→platform, platform→tenant-business | all cross-zone calls denied unless explicitly contracted | API | PASS |
| T30-05 | WZ-05 | remove permission/revoke session/rotate+block client then reuse stale auth | stale access rejected | API | PASS |
| T30-06 | WZ-06 | Collection Officer queue→case→promise/dispute/action→timeline→close | authoritative state visible; immutable timeline; no placeholder | Browser+API | PASS |
| T30-07 | WZ-07 | create promise/dispute/action without payment | invoice paid/outstanding unchanged | API+DB | PASS |
| T30-08 | WZ-08 | allocate valid then over-allocate/wrong customer/wrong currency | valid exact; invalid rejected; balances unchanged on rejection | API+DB | PASS |
| T30-09 | WZ-09 | replay allocation command then reverse/replay reverse | one allocation effect; exact restored balances; no double reversal | API+DB | PASS |
| T30-10 | WZ-10 | non-integer money + timezone/business-date overdue boundary | no precision loss; UI equals backend projection | API+Frontend | PASS |
| T30-11 | WZ-11 | ingest same idempotency key/body twice then changed body | one business effect; replay stable; changed body conflict | service API+DB | PASS |
| T30-12 | WZ-12 | published schema/mapping ingests representative custom payload | exact canonical CUSTOMER/INVOICE/PAYMENT values | service API+DB | PASS |
| T30-13 | WZ-13 | import batch with valid+invalid records | documented partial/atomic behavior; durable safe diagnostics | API+DB+UI | PASS |
| T30-14 | WZ-14 | authenticate client, rotate/block, retry ingestion with old/new credentials/scopes | only currently valid scoped credential succeeds | service API | NOT_RUN |
| T30-15 | WZ-15 | customer detail after contract/invoice/payment/collection changes | accepted D05 Customer 360 links/summary refresh from authoritative APIs; no contradictory state | Browser+API | NOT_RUN |
| T30-16 | WZ-16 | two actors edit same versioned aggregate | stale command 409; first write preserved; UI reload/reapply required | API+Browser | NOT_RUN |
| T30-17 | WZ-17 | prepare overdue recipient, fully pay+allocate, recheck | recipient SKIPPED reason PAID; no message delivery | API+DB | PASS |
| T30-18 | WZ-18 | Alpha campaign selection contains Beta customer/segment | rejected/ignored per explicit contract without disclosure; no Beta recipient | API+DB | NOT_RUN |
| T30-19 | WZ-19 | publish v1, prepare/materialize, create/edit v2 | existing message/run remains bound to immutable intended content/version | API+DB | NOT_RUN |
| T30-20 | WZ-20 | malicious HTML/script/expression/control chars through template APIs | rejected/sanitized exactly per policy; preview/materialization safe | API+Frontend | NOT_RUN |
| T30-21 | WZ-21 | Alpha uses Beta file/asset in template/message and requests metadata/content | non-disclosure; no storage key/bucket leakage | API+DB | NOT_RUN |
| T30-22 | WZ-22 | required attachment PENDING then FAILED/READY | provider calls 0 for blocked states; READY allows one | worker+DB | PASS |
| T30-23 | WZ-23 | 100 duplicate events, 8 workers | exactly one provider call; one SENT; sentCount=1 | concurrency integration | PASS |
| T30-24 | WZ-24 | transient failure→retry→success and retry exhaustion | exact Message status/attempt/retry + CampaignRun counters transactionally consistent | worker+DB | NOT_RUN |
| T30-25 | WZ-25 | provider accepts then response is lost/ambiguous | accepted D15: no unsafe blind resend; stable delivery key; explicit safe handling/state | adapter+worker | PASS |
| T30-26 | WZ-26 | stale PROCESSING + concurrent recovery/late event | one legal final transition; no backward terminal transition/double count | recovery+DB | NOT_RUN |
| T30-27 | WZ-27 | competing outbox publishers + retry/duplicate consumer delivery | one claim owner; eventual publish; downstream idempotent business effect | PostgreSQL+RabbitMQ | PASS |
| T30-28 | WZ-28 | Support opens message list/detail for own/foreign tenant | own data masked/safe; foreign hidden; no fabricated attempt timeline | Browser+API | NOT_RUN |
| T30-29 | WZ-29 | traverse every REAL/PARTIAL primary route with persona/deep link/filter/error | correct page/API/permission; no unexpected placeholder; URL state stable | Frontend | NOT_RUN |
| T30-30 | WZ-30 | clean DB bootstrap then full Golden Journey to mock adapter | all migrations/contracts work together; monitoring reflects final state | Full stack | PASS |

## T30-30 Golden Journey assertions

Tenant registration/admin -> persona permissions -> service client -> schema/mapping/source ACTIVE -> idempotent CUSTOMER/INVOICE/PAYMENT -> allocation -> overdue collection case -> collection action -> published template -> campaign -> run -> eligibility recheck -> message/attachment readiness -> outbox/broker/worker/router -> deterministic adapter -> message monitoring.

At every boundary assert tenantId ownership, stable IDs/idempotency, authoritative monetary state, no forbidden PII/secrets, and exact final counters.

## Executed evidence checkpoint

The PASS rows above were executed together on branch SHA `a3dbd65bc20156eb044d6edea2485edde8f45401` in GitHub Actions run `36238480310` (backend verify and frontend both successful). T30-30 is exercised by `RabbitMqMessageDeliverySmokeIntegrationTest#outboxTraversesRealRabbitTopologyIntoDeliveryWorker`, which now composes service credential/token issuance, ACTIVE CUSTOMER/INVOICE/PAYMENT integration sources, idempotent ingestion, canonical finance/allocation, collections, published template, campaign materialization, outbox, real RabbitMQ, delivery worker and deterministic simulated provider. Rows still marked NOT_RUN remain fail-closed and must not be inferred from this checkpoint.
