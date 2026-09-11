# Slice 6 — Campaign delivery counters and completion

## Цель

Сделать delivery counters `CampaignRun` реальным источником состояния выполнения кампании.

## Что сделать

- добавить доменные методы для изменения `sentCount`, `failedCount`, `retryCount`;
- обновлять counters в той же транзакции, где `Message` меняет delivery status;
- блокировать `CampaignRun` при изменении counters;
- завершать run только по durable database state;
- сохранить текущий invariant:

```text
sentCount + failedCount + skippedCount == recipientCount
```

## Правила

- `sentCount` увеличивается только при реальном переходе в `SENT`;
- `failedCount` — только при первом переходе в `FAILED`;
- `retryCount` — количество фактически назначенных retry;
- broker redelivery не должен увеличивать counters повторно;
- использовать одинаковый lock order во всех местах, например `Message -> CampaignRun`.

## Не делать

- не считать Rabbit ACK признаком завершения кампании;
- не вычислять completion по пустой очереди;
- не добавлять публичные setters для counters.

## Тесты

- SENT увеличивает counter один раз;
- FAILED увеличивает counter один раз;
- retry увеличивает retryCount без terminal completion;
- duplicate/redelivery не double-count;
- CampaignRun завершается только когда все recipient outcomes terminal.

## Definition of Done

Counters и completion устойчивы к повторной обработке и конкурентным worker-ам. `mvn verify` зелёный.
