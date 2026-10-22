# AlertLevelServiceApp

## Overview

Tracks the hospital Emergency Status (0-8, 8 = full Code Blue).

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

REST: called by `staffing-service` (`../staffing-service`) to read the current
status when computing on-call schedules — see [Integration contracts](../README.md#integration-contracts)
in the root README for the endpoint shapes.

## Project structure

```
alert-level-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/AlertLevelServiceApp.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/alert-level-service.jar
```

Listens on port `7032` (override with `PORT`). Serves:

- `GET /alert-level` — current status, e.g. `{"level":5,"updatedAt":"..."}`
- `PUT /alert-level` / `POST /alert-level` — set the status from
  `{"level":0-8}`; anything outside 0–8 is rejected with `400`

## Test

```
mvn test
```

JUnit 5 tests cover level parsing and the 0–8 range validation
(`AlertLevelServiceAppTest`).

Up check:

```
curl http://localhost:7032/health   # -> OK
```
