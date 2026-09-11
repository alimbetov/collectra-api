# Campaign Core — implementation slice

This slice separates campaign definition from execution history:

`Campaign -> CampaignRun -> CampaignRecipient snapshot -> eligibility recheck`

## Scope

- one published template version and one channel per campaign;
- EMAIL only in this slice;
- simple selection by customer IDs, segment IDs, overdue days and outstanding amount;
- one immutable recipient snapshot per invoice/customer inside a run;
- primary active email is preferred, otherwise first active email;
- eligibility is rechecked before the communication slice creates messages;
- skip reasons: `CUSTOMER_INACTIVE`, `NO_CONTACT`, `PAID`.

## Deliberate limits

No recurring scheduler, query DSL, communication message creation, RabbitMQ publication or email sending is added here. A run reaches `READY` after snapshot preparation. The following communication slice will consume only recipients that pass eligibility recheck.
