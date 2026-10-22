# StaffingServiceApp

## Overview

Provides on-call schedules for doctors based on ward and status.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service publishes to the ActiveMQ topic `staffing-events-topic` — see [`../common/`](../common). Broker URL and topic name come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.

REST: calls `ward-service` (`../ward-service`) to validate the ward and
`alert-level-service` (`../alert-level-service`) to read the current Emergency
Status before computing a schedule — see [Integration contracts](../README.md#integration-contracts)
in the root README for the endpoint shapes.

## Project structure

```
staffing-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── StaffingServiceApp.java
    ├── OnCallSchedule.java
    ├── ScheduleCalculator.java
    ├── RosterRequest.java
    ├── WardServiceClient.java
    ├── AlertLevelClient.java
    ├── StaffingEvent.java
    ├── StaffingPublisher.java
    ├── UnknownWardException.java
    ├── DownstreamUnavailableException.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/staffing-service.jar
```

Listens on port `7033` (override with `PORT`; downstreams are `WARD_URL` and
`ALERT_LEVEL_URL`). Serves:

- `GET /staffing/{wardId}` — compute the on-call schedule for the ward's current
  Emergency Status (`404` unknown ward, `503` downstream unavailable)
- `POST /staffing/{wardId}/roster` — recompute and publish a `ROSTER_UPDATED`
  event to `staffing-events-topic` (`201`)
- `GET /staffing` — latest published roster events

## Test

```
mvn test
```

JUnit 5 tests cover on-call sizing (`ScheduleCalculatorTest`): headcount by
Emergency Status and the day/night shift split; plus roster-request validation
(`RosterRequestTest`) and the published event shape (`StaffingEventTest`).

Up check:

```
curl http://localhost:7033/health   # -> OK
```
