# WardServiceApp

## Overview

Provides lists of wards and departments.

Part of the [HealthSafe](../README.md) project. Independent Maven module, no
parent pom.

MQ: this service subscribes to the ActiveMQ topic `staffing-events-topic` — see [`../common/`](../common) — and publishes to the ActiveMQ queue `equipment-failure-queue` when it detects an equipment failure on one of its wards, consumed by [`../equipment-alert-service`](../equipment-alert-service). Broker URL, topic name, and queue name come from the common `co.wethinkcode.healthsafe.mq.MqConfig` class alongside it in this module.

REST: called by `staffing-service` (`../staffing-service`) and `alert-level-service` (`../alert-level-service`) — see [Integration contracts](../README.md#integration-contracts) in the root README for the endpoint shapes.

## Project structure

```
ward-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── WardServiceApp.java
    ├── Ward.java
    ├── WardSource.java
    ├── WardRegistry.java
    ├── IngestionClient.java
    ├── StaffingEvent.java
    ├── StaffingEventCache.java
    ├── StaffingEventSubscriber.java
    ├── EquipmentFailureRequest.java
    ├── EquipmentFailureAlert.java
    ├── EquipmentFailurePublisher.java
    ├── BrokerUnavailableException.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/ward-service.jar
```

Listens on port `7031` (override with `PORT`; the ingestion source is
`INGESTION_URL`). Populates its ward list from the ingestion-service on a
background thread that retries, so it can start before ingestion is up (callers
get `503` until the first load succeeds). Serves:

- `GET /wards`, `GET /wards/{id}` — wards (`404` if unknown)
- `GET /departments` — distinct departments
- `GET /wards/{id}/staffing` — latest staffing event received from the topic
  (`404` until one arrives)
- `POST /wards/{id}/equipment-failure` — publish an alert to
  `equipment-failure-queue` (`202`; `400` missing equipment, `404` unknown ward,
  `503` broker down)
- `POST /wards/reload` — force a re-fetch from ingestion-service

## Test

```
mvn test
```

JUnit 5 tests cover the ward registry and ingestion client, the staffing cache
and the live topic round trip (`StaffingEventSubscriberTest`, which needs the
broker from `common/` and skips itself when none is reachable), and
equipment-failure request validation.

Up check:

```
curl http://localhost:7031/health   # -> OK
```
