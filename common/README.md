# common — Asynchronous Decoupling (MQ)

## Overview

Topic: `staffing-events-topic`

Staffing updates are broadcast as Events via the broker to decouple the frontend from the Staffing Service.

Part of the [HealthSafe](../README.md) project. Holds the ActiveMQ broker shared
by the services below — not a service itself, so it has no port of its own.

- `staffing-events-topic` — producer: `staffing-service` (`../staffing-service`);
  consumer: `ward-service`
- `equipment-failure-queue` — producer: `ward-service`; consumer:
  `equipment-alert-service` (`../equipment-alert-service`)

Broker URL, topic name, and queue name are shared via a common
`co.wethinkcode.healthsafe.mq.MqConfig` class (`BROKER_URL`, `TOPIC`, `QUEUE`,
each overridable with `MQ_BROKER_URL`, `MQ_TOPIC`, `MQ_QUEUE`). The class is
duplicated into each service's own source tree (each copy declaring only the
constants that service uses) — each service here is an independent Maven project
with no shared parent pom, so it is copied rather than imported from one place.

## Project structure

```
common/
├── docker-compose.yml
└── README.md
```

This folder holds the broker config and notes only — the actual publish/subscribe
code belongs in the producer/consumer services listed above (their poms already
depend on `activemq-client`, and each already has
`src/main/java/co/wethinkcode/healthsafe/mq/MqConfig.java`).

## Build

Nothing to build here directly — this folder just brings up the broker used by the
services listed above.

## Run

```
docker compose up -d
```

- Broker URL for clients: `tcp://localhost:61616`
- Web console: http://localhost:8161 (default admin/admin)

Then start the producer/consumer services as usual (`mvn package && java -jar ...`
from their own directories at the project root).

## Test

```
docker compose ps          # confirm the broker container is healthy
```

(`ward-service`'s topic test also uses this broker when it is up; it skips
itself otherwise, so `mvn test` works without it.)

## What's wired up

- **`staffing-events-topic` (topic, broadcast)** — `staffing-service` publishes
  `ROSTER_UPDATED` events on `POST /staffing/{wardId}/roster`; `ward-service`
  keeps a durable subscription (client id `ward-service`) and exposes the latest
  event at `GET /wards/{id}/staffing`.
- **`equipment-failure-queue` (queue, guaranteed delivery)** — `ward-service`
  publishes a `PERSISTENT` alert on `POST /wards/{id}/equipment-failure`;
  `equipment-alert-service` consumes it with `CLIENT_ACKNOWLEDGE` and only acks
  after processing, so a crash mid-processing leaves it for redelivery.
- Both background clients install a JMS exception listener and reconnect when the
  broker goes away. The queue consumer also offers `GET /alerts` for what it has
  processed so far.
