# Slice N — Title

Status: Planned

Depends on: `slice-XX-...`

Suggested branch: `feat/...`

## Цель

Одним абзацем: какой законченный технический результат должен дать slice.

## Уже есть

Короткий список существующего кода, который переиспользуем и не строим заново.

## Scope

Что конкретно нужно изменить или добавить.

## Основной поток

```text
step A
  -> step B
  -> step C
```

## Инварианты и правила

Только правила, нарушение которых приводит к багам, race condition, утечке tenant data или расхождению состояния.

## Не входит

Явно зафиксировать соседние задачи, которые остаются для следующих slices.

## Тесты

Минимальный обязательный набор unit/integration/architecture tests.
PostgreSQL tests обязаны соблюдать общий
[`integration-test-runtime.md`](integration-test-runtime.md), включая connection
budget и reuse Spring contexts.

## Definition of Done

Небольшой список наблюдаемых критериев готовности. Последний критерий — зелёный `mvn verify`.
