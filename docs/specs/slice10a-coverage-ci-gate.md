# Slice 10A Critical-Core Coverage CI Gate

The backend CI runs the existing `slice10a-coverage` Maven profile as part of the
normal PostgreSQL/Testcontainers verification. This avoids executing the complete
integration suite twice while making the declared thresholds mandatory:

- line coverage: at least 95%;
- branch coverage: at least 90%;
- scope: the critical delivery classes already listed in `pom.xml`.

The thresholds and includes are not weakened to repair CI. The JaCoCo report is
uploaded as `slice10a-jacoco`, including on a failed check, for diagnosis.

Local equivalent:

```bash
mvn --batch-mode --no-transfer-progress clean verify -Pslice10a-coverage
```
