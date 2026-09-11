# Integration-test runtime contract

Status: REQUIRED BEFORE SLICE 3

## 1. Причина

Локальный полный `mvn verify` после merge Slice 2 выполнил 321 тест, после чего
`MessageProcessingIntegrationTest` не смог создать Spring context:

```text
org.postgresql.util.PSQLException:
FATAL: sorry, too many clients already
```

В одном Maven JVM было создано как минимум 11 Hikari pools. Несколько уникальных
`@SpringBootTest` contexts остаются в Spring TestContext cache, а стандартный
`maximumPoolSize = 10` позволяет им суммарно исчерпать PostgreSQL
`max_connections = 100`.

Это не ошибка state machine Message, но это воспроизводимый дефект общей тестовой
инфраструктуры. Увеличение `max_connections` только маскирует отсутствие
connection budget и не является основным исправлением.

## 2. Обязательная конфигурация test profile

Создать единый `src/test/resources/application-test.yml` или добавить
эквивалентные свойства в существующий test profile:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 3
      minimum-idle: 0
```

`maximum-pool-size = 3` выбран намеренно:

- concurrent persistence tests могут использовать две параллельные транзакции;
- остаётся одна connection для служебной операции/инициализации;
- 11 одновременно закэшированных contexts используют не более 33 connections,
  оставляя PostgreSQL безопасный запас.

Если новый тест действительно требует больше трёх одновременных connections, он
обязан локально обосновать отдельный pool size и учитывать общий budget, а не
повышать глобальный default.

## 3. Spring context contract

- Все PostgreSQL integration tests наследуются от существующего
  `AbstractIntegrationTest` и используют один static PostgreSQL Testcontainer на
  Maven JVM.
- Не объявлять новый PostgreSQL container на test class.
- Не использовать новый `@SpringBootTest(properties = ...)`, `@Import` или
  `@TestConfiguration`, если тот же bean можно предоставить общей test
  configuration: каждый уникальный context key создаёт отдельный Hikari pool.
- Конфигурации `Clock`, storage и provider doubles должны переиспользоваться между
  тестами соответствующего типа.
- `@DirtiesContext` применять только при доказанной утечке изменённого context
  state. Массовое закрытие contexts через `@DirtiesContext` не заменяет pool
  budget и заметно замедляет suite.
- Тест обязан освобождать собственные executors, streams и внешние ресурсы в
  `finally`/lifecycle callback.

## 4. Проверка исправления

Repair PR выполняется отдельно от Slice 3 и должен доказать:

1. `MessageProcessingIntegrationTest` проходит отдельно;
2. полный `mvn verify` проходит в одной Maven JVM;
3. в логе нет `too many clients already`;
4. test profile не повышает PostgreSQL `max_connections`;
5. production datasource configuration не изменена.

Повторный запуск только упавшего класса недостаточен: дефект проявляется из-за
накопления закэшированных contexts в полном suite.

## 5. Definition of Done

- connection budget задан централизованно для profile `test`;
- concurrent tests сохраняют работоспособность при pool size 3;
- полный suite стабильно проходит локально и в CI;
- новые Slice 3–8 используют этот contract;
- repair оформлен отдельным узким PR в `main`.
