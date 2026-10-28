# IngestionServiceApp

## Overview

Parses and cleans `wards-outdated.csv`, a messy legacy export of wards, wings, and specialist departments data, and is the
first stop in the HealthSafe pipeline. Independent Maven module, no parent pom.

Part of the [HealthSafe](../README.md) project.

REST: exposes the cleaned records for `ward-service` (`../ward-service`) to
consume — see [Integration contracts](../README.md#integration-contracts) in the
root README for the endpoint shape.

## Example: one row cleaned

Input (`wards-outdated.csv`, row 6):

```
w-05,east wing ,PAEDIATRICS,five
```

Expected shape after cleaning (exact field names are up to you — this illustrates
the *kind* of transform expected, not a fixed schema to match exactly):

```json
{
  "wardId": "W-05",
  "wing": "East Wing",
  "department": "Paediatrics",
  "bedsAvailable": null,
  "notes": "bedsAvailable was non-numeric ('five') — flagged for follow-up"
}
```

Note this row is also a near-duplicate of `W-05` two rows above it (same real ward,
different ID casing and field values) — deciding how to merge or flag duplicates
like this is part of the exercise.

## Known data issues

`wards-outdated.csv` is deliberately messy — cleaning it is the point of this service. Look
out for (and handle) at least:

- **Inconsistent casing** in IDs, names, and status/category values (`Active` /
  `active` / `ACTIVE`)
- **Padding** — leading/trailing spaces, and the occasional double space, inside
  fields
- **Duplicate records** for the same real-world entity, written with a different ID
  casing/format and/or slightly different field values
- **Missing / placeholder values** — blank fields, `N/A`, `n/a`, `TBD`, `unknown`,
  `-`, `NaN`
- **Invalid or non-numeric values** in numeric columns (negative counts, spelled-out
  numbers, unrealistic values)
- **Naming/spelling variants** for the same thing (e.g. regional spelling
  differences, synonyms)

## Project structure

```
ingestion-service/
├── pom.xml
└── src/main/
    ├── java/co/wethinkcode/healthsafe/IngestionServiceApp.java
    └── resources/wards-outdated.csv
```

## Build

```
mvn package
```

## Run

```
java -jar target/ingestion-service.jar
```

Listens on port `7030` (override with `PORT`). Cleans `wards-outdated.csv` at
startup and serves the result:

- `GET /wards` — the cleaned ward records (JSON array) for `ward-service`
- `GET /wards/{id}` — one ward by id (case-insensitive), `404` if unknown
- `GET /wards/report` — the cleaning summary (`totalDataRows`, `cleanedRecords`,
  `duplicatesMerged`, `rejectedRows`)

See the root README's *Assumptions & trade-offs* for the cleaning rules.

## Test

```
mvn test
```

JUnit 5 tests cover field normalisation (`FieldNormalizersTest`) and the
cleaning/duplicate-merge pass (`WardCleanerTest`).

Up check:

```
curl http://localhost:7030/health   # -> OK
```
