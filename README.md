# HealthSafe

## Overview

Hospital ward status and emergency staffing schedules.

Domain entities: wards, wings, specialist departments.

Every class in this repo lives in a single flat package, `co.wethinkcode.healthsafe`. HealthSafe is built
as a small set of independent services, following a growth path from simple data
cleanup through synchronous REST calls to asynchronous MQ decoupling and alerting:

1. clean a messy legacy CSV export (`wards-outdated.csv`) — handled by **IngestionServiceApp**
2. serve it up and act on it, via three REST services calling each other directly
   over HTTP
3. decouple the relevant services with an ActiveMQ topic (`staffing-events-topic`) instead of
   direct calls — shared broker setup lives in [`common/`](common)
4. raise the alarm on failure — handled by **EquipmentAlertServiceApp**

| Service | Folder | Port | Role |
|---|---|---|---|
| IngestionServiceApp | [`ingestion-service/`](ingestion-service) | 7030 | Parses and cleans `wards-outdated.csv` |
| WardServiceApp | [`ward-service/`](ward-service) | 7031 | Provides lists of wards and departments. |
| AlertLevelServiceApp | [`alert-level-service/`](alert-level-service) | 7032 | Tracks the hospital Emergency Status (0-8, 8 = full Code Blue). |
| StaffingServiceApp | [`staffing-service/`](staffing-service) | 7033 | Provides on-call schedules for doctors based on ward and status. |
| EquipmentAlertServiceApp | [`equipment-alert-service/`](equipment-alert-service) | 7034 | uses a Queue to guarantee delivery of critical medical equipment failure alerts. |

Plus [`common/`](common) (no port) — the shared ActiveMQ broker, with notes on both
channels: staffing updates are broadcast on `staffing-events-topic` to decouple the
frontend from the Staffing Service, and equipment-failure alerts go through
`equipment-failure-queue` for guaranteed delivery.

**Status:** complete — all four stages are implemented and verified end to end
against a live ActiveMQ broker: cleaning/ingestion, the three synchronous REST
services, topic-based decoupling of staffing updates, and queue-based
equipment-failure alerting. See [Assumptions & trade-offs](#assumptions--trade-offs).

## Your task

> Kept for reference: this is the original brief. It is now complete — the
> TODOs mentioned below are implemented, and each service's `## Test` section
> describes the tests that exist rather than how to add them.

Each stage below builds on the last — do them in order. Every service already builds
and runs (`/health` returns `OK`); your job is to fill in the `TODO`s.

1. **Ingestion** (required) — in `IngestionServiceApp`, read and clean
   `wards-outdated.csv` (see [ingestion-service/README.md](ingestion-service/README.md)
   for the known data issues and a worked example) and expose the cleaned records
   over REST for `ward-service` to consume.
2. **REST services** (required) — implement `ward-service`, `alert-level-service`,
   and `staffing-service` per the [Integration contracts](#integration-contracts)
   below: wards/departments lookup, Emergency Status tracking, and on-call
   scheduling that calls the other two services synchronously over HTTP.
3. **MQ decoupling** (stretch) — replace the synchronous call from `ward-service`
   to `staffing-service` with the `staffing-events-topic` broadcast described in
   [common/README.md](common/README.md), so staffing updates reach `ward-service`
   asynchronously instead.
4. **Alerting** (stretch) — have `ward-service` publish to the
   `equipment-failure-queue` when it detects an equipment failure, and implement
   `equipment-alert-service` as the guaranteed-delivery consumer (see
   [equipment-alert-service/README.md](equipment-alert-service/README.md)).

Stage 1-2 are the required core; stages 3-4 are where you can show judgment about
when to reach for a queue/topic instead of a direct call. There's no fixed time
limit, but budget your effort so you have a working stage 1-2 before spending time
on 3-4 — a complete core beats a half-done everything.

Automated tests aren't required, but are a good way to show your work — see each
service's `## Test` section for how to add JUnit 5.

## Integration contracts

Endpoint shapes below are illustrative, not a fixed spec to match byte-for-byte —
reasonable field names/status codes are fine as long as the calling service can
consume them.

| From | To | Call | Purpose |
|---|---|---|---|
| `ward-service` | `ingestion-service` | `GET /wards` → cleaned ward records | Populate its own ward/department list |
| `staffing-service` | `ward-service` | `GET /wards/{id}` → `404` if unknown | Validate the ward before scheduling |
| `staffing-service` | `alert-level-service` | `GET /alert-level` → `{ "level": 0-8 }` | Read current Emergency Status to size the on-call schedule |
| `staffing-service` | `ward-service` (topic, stage 3) | publish to `staffing-events-topic` | Broadcast a schedule/status change |
| `ward-service` (topic, stage 3) | — | subscribe to `staffing-events-topic` | React to staffing updates without polling |
| `ward-service` (queue, stage 4) | `equipment-alert-service` | publish to `equipment-failure-queue` | Guarantee delivery of an equipment failure alert |

## Assumptions & trade-offs

Where the brief left room for judgment, these are the choices made and why:

- **Duplicate wards** (`W-05` vs `w-05` with differing fields) are *merged*
  first-valid-wins rather than the later row silently overwriting the earlier
  one. The merged record's `notes` list records `merged duplicate row N` and
  `sourceRows` lists every contributing CSV row, so a reviewer can always trace
  what was combined. Nothing is discarded without a note.
- **Unrealistically high bed counts** (`> 200`) are treated as suspected data
  errors: the value is kept but flagged in `notes` for human follow-up, rather
  than rejected outright or trusted blindly.
- **Missing/placeholder values** (`N/A`, `TBD`, blank, `-`, `NaN`) all collapse
  to a single `null` representation so downstream code has one thing to test.
- **A malformed row never crashes the parse**: bad numeric values (`five`,
  `full`, negatives) degrade to `null` plus a note. The ingestion report always
  returns (`totalDataRows`/`cleanedRecords`/`duplicatesMerged`/`rejectedRows`).
- **Topic vs queue** is a deliberate contrast, not an accident:
  `staffing-events-topic` is a *broadcast* — any number of interested services
  (here `ward-service`) subscribe, and losing a missed message is acceptable
  because a newer roster supersedes it. `equipment-failure-queue` is
  *guaranteed, single-consumer, at-least-once* — an equipment failure must reach
  the alerting consumer exactly once and must not be lost if that consumer is
  briefly down. The producer sends `PERSISTENT` messages; the consumer uses
  `CLIENT_ACKNOWLEDGE` and only acks after the alert is fully processed, so a
  crash mid-processing leaves the message on the queue for redelivery.
- **`ward-service` uses a durable topic subscription** (client id
  `ward-service`), so staffing updates published while it is restarting are not
  lost.
- **A dropped broker connection is noticed, not waited out**: both background
  MQ clients flag a lost connection from the JMS exception listener and tear the
  session down so the retry loop reconnects. An async listener does not throw at
  the thread parked in the consume loop, so without this the service would sit
  on a dead connection indefinitely.
- **Configuration is environment-overridable** — `PORT`, `INGESTION_URL`,
  `WARD_URL`, `ALERT_LEVEL_URL`, `MQ_BROKER_URL`, `MQ_TOPIC`, `MQ_QUEUE` — so
  the stack can run on non-default ports (useful when several copies share a
  machine) and against a non-default broker.

## Project structure

```
healthsafe/
├── README.md
├── .gitignore
├── ingestion-service/          (port 7030)
│   ├── pom.xml
│   ├── README.md
│   └── src/main/
│       ├── java/co/wethinkcode/healthsafe/IngestionServiceApp.java
│       └── resources/wards-outdated.csv
├── ward-service/          (port 7031)
├── alert-level-service/          (port 7032)
├── staffing-service/          (port 7033)
├── common/
│   ├── docker-compose.yml
│   └── README.md
└── equipment-alert-service/          (port 7034)
```

## Build

Requirements: Java 17+, Maven 3.8+, Docker (for the broker in `common/`).

Every folder here (`ingestion-service/`, each domain service, and `equipment-alert-service/`) is
an **independent** Maven project — there is no parent/aggregator pom. Build one at a
time, e.g.:

```
cd ward-service
mvn package
```

...or build every module in the repo in one pass from the project root:

```
find . -name pom.xml -execdir mvn -q package \;
```

## Run

```
# ingestion
cd ingestion-service && mvn package && java -jar target/ingestion-service.jar

# domain services, each in its own terminal
# terminal 1
cd ward-service && mvn package && java -jar target/ward-service.jar
# terminal 2
cd alert-level-service && mvn package && java -jar target/alert-level-service.jar
# terminal 3
cd staffing-service && mvn package && java -jar target/staffing-service.jar

# MQ broker (needed once the MQ-aware services above are wired up)
cd common && docker compose up -d

# alerting
cd equipment-alert-service && mvn package && java -jar target/equipment-alert-service.jar
```

| Service | Port |
|---|---|
| IngestionServiceApp (`ingestion-service`) | 7030 |
| WardServiceApp (`ward-service`) | 7031 |
| AlertLevelServiceApp (`alert-level-service`) | 7032 |
| StaffingServiceApp (`staffing-service`) | 7033 |
| EquipmentAlertServiceApp (`equipment-alert-service`) | 7034 |

## Test

JUnit 5 tests cover the logic that is easiest to get subtly wrong and hardest to
eyeball. Run a module's tests with `mvn test` (or `mvn package`, which runs them
too).

| Module | Tests | Covers |
|---|---|---|
| `ingestion-service` | 13 | CSV field normalisation, duplicate merging, malformed rows |
| `ward-service` | 25 | ward registry, ingestion client, staffing cache + live topic round trip, equipment-failure body |
| `alert-level-service` | 4 | Emergency Status 0–8 parsing/validation |
| `staffing-service` | 12 | on-call sizing, roster request parsing, event shape |
| `equipment-alert-service` | 4 | alert parsing and process-then-acknowledge |
| **Total** | **58** | |

The `ward-service` topic test needs a broker (`common/docker-compose.yml`) and
skips itself when none is reachable, so `mvn test` stays green without one.

Every running service also exposes `/health`, so sanity-check manually:

```
curl http://localhost:7030/health   # -> OK
```

To add tests to a module that has none yet, add JUnit 5 and Surefire to its
`pom.xml`:

```xml
<dependency>
  <groupId>org.junit.jupiter</groupId>
  <artifactId>junit-jupiter</artifactId>
  <version>5.10.2</version>
  <scope>test</scope>
</dependency>
```

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.2.5</version>
</plugin>
```

then add tests under that module's `src/test/java/...` and run:

```
mvn test
```

verification code
WTC-L6MJ72L6
