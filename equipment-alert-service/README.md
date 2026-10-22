# EquipmentAlertServiceApp

## Overview

Uses a Queue to guarantee delivery of critical medical equipment failure alerts.

Part of the [HealthSafe](../README.md) project — its alerting service.
Independent Maven module, no parent pom.

Mechanism: ActiveMQ Queue (guaranteed delivery)

Queue: `equipment-failure-queue`

- Producer: `ward-service` (`../ward-service`) — publishes when it detects an
  equipment failure on one of its wards.
- Consumer: this service.

Broker URL and queue name are shared via a common `co.wethinkcode.healthsafe.mq.MqConfig`
class (`BROKER_URL`, `QUEUE`). It's identical in every participating service's own
source tree — each service here is an independent Maven project with no shared
parent pom, so the common package is duplicated rather than imported from one place.

A queue (not a topic) is used deliberately: each failure alert must be delivered to
exactly one consumer and processed at least once, even if this service is briefly
down — unlike the broadcast `staffing-events-topic` in [`../common/`](../common),
where every subscriber gets every message.

## Project structure

```
equipment-alert-service/
├── pom.xml
└── src/main/java/co/wethinkcode/healthsafe/
    ├── EquipmentAlertServiceApp.java
    ├── EquipmentAlertConsumer.java
    ├── AlertProcessor.java
    ├── EquipmentFailureAlert.java
    └── mq/
        └── MqConfig.java
```

## Build

```
mvn package
```

## Run

```
java -jar target/equipment-alert-service.jar
```

Listens on port `7034` (override with `PORT`). Connects to the broker on a
background thread that retries, so it starts cleanly even if the broker is not up
yet, and reconnects if the broker goes away. Serves:

- `GET /health` — liveness
- `GET /alerts` — the equipment-failure alerts consumed so far

## Test

```
mvn test
```

JUnit 5 tests cover alert parsing and the process-then-acknowledge decision
(`AlertProcessorTest`): a malformed payload reports "not handled", so the
consumer leaves it unacknowledged and the broker redelivers it.

Manual end to end: with the broker running, trigger a failure and check it is
consumed:

```
# ward-service publishes to equipment-failure-queue when it detects a failure
curl -X POST http://localhost:7031/wards/W-05/equipment-failure \
  -H 'Content-Type: application/json' \
  -d '{"equipment":"Ventilator-3","detail":"not powering on"}'

curl http://localhost:7034/alerts   # -> the alert appears once consumed
```
