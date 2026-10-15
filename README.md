# Beadle-ProTech

Beadle-ProTech is the service between Beadle and ProTech, BBH's ServiceNow. It raises the production changes of
Beadle-GUI as ProTech change requests with their tasks, reads them back as they move through the approval
workflow, updates them, reminds their approvers and looks up the people, departments, assignment groups, releases,
configuration items, incidents, problems and clients of ProTech. It signs in to ProTech with the generic service
account of Beadle, keeps a call log of every call it makes and calls Beadle-GUI back when ProTech moves a change
Beadle raised or read. Without a ProTech address it runs a demo ProTech that drives the workflow by the clock.

[docs/CONTRACTS.md](docs/CONTRACTS.md) is the contract between the three Beadle repositories (Beadle-GUI,
Beadle-Internal and Beadle-ProTech): the REST API of this service with its bodies, the headers every call carries
and the callback to Beadle-GUI. The code follows it to the letter.
[docs/PROTECH-MAPPING.md](docs/PROTECH-MAPPING.md) tells how a change is stored in ProTech: the ServiceNow Table API
calls behind every endpoint, the table and field of every field of a change, its tasks and approvals, the state
values, the lookup tables, the poll that finds what moved, and how each name is configured.

## API

Every call carries `Authorization: Bearer <BEADLE_SERVICE_TOKEN>` (otherwise `401`), and from Beadle-GUI
`X-Beadle-User` (the signed-in user, only logged) and `X-Correlation-Id` (one per user action, logged and passed on
in callbacks; a fresh one is minted when it is missing). Answers are JSON, errors `application/problem+json` with
`status`, `title`, `detail` and, for validation, `problems: [{"field", "message"}]`. A request ProTech would refuse
is refused with `400` and the fields at fault before it reaches ProTech; what ProTech refuses itself (a change it
does not have, a closed change) answers `409` with ProTech's reason, and ProTech out of reach, a failed sign-in or
a `5xx` of ProTech answers `502`. `Change` and `ChangeTask` are the JSON of the records `ProductionChange` and
`ChangeTask` that docs/CONTRACTS.md describes.

| Method and path | Body | Answer |
|---|---|---|
| `GET /api/protech/status` | | `{"connected": false, "mode": "demo"}` or `{"connected": true, "mode": "live"}` |
| `POST /api/protech/changes` | a `Change` (the draft) | `201` `{"number": "CHG0001234", "url": "https://.../CHG0001234" or null}` |
| `POST /api/protech/changes/{number}/tasks` | a `ChangeTask` | `201` `{"number": "CTASK0001234"}` |
| `POST /api/protech/changes/read` | `[Change, ...]`, the changes Beadle-GUI knows | `{"CHG0001234": Change, ...}`, each as ProTech holds it now |
| `PUT /api/protech/changes/{number}` | a `Change` | `204`; a task left out is canceled, a task without a number is created |
| `POST /api/protech/changes/{number}/reminders` | `{"role": "BUSINESS"}` or `{"task": "CTASK0001234"}` | `["Rebecca Lawson", ...]`, the people reminded |
| `GET /api/protech/lookups/{kind}?q=&limit=20` | | `[{"value": "...", "detail": "..."}]`; `kind` is `users`, `departments`, `assignment-groups`, `releases`, `configuration-items`, `incidents`, `problems` or `clients`, `limit` 1 to 100 |
| `GET /api/protech/call-log?correlationId=&from=&to=&page=0&size=50` | | the call log, newest first, for diagnosis (`from` inclusive, `to` exclusive, `size` up to 500) |
| `GET /actuator/health` | | open, without a token |

### Callbacks to Beadle-GUI

Every change raised or read through the API is written down in `BEADLE_PROTECH_CHANGE` with the state Beadle-GUI
knows, the time up to which it knows what ProTech did, and the correlation id of the user action. A ticker looks
every `beadle.protech.tick` (10 seconds) at ProTech: the demo ProTech at every tick, the live one once
`beadle.protech.poll` (one minute) has passed, with three Table API queries for the changes, change tasks and
approvals updated since the earliest last look less a minute. For each change that moved past what Beadle-GUI knows
it posts one callback

```json
{"number": "CHG0001234", "event": "STATE_CHANGED", "state": "IMPLEMENTATION", "at": "2026-10-15T09:00:00Z", "correlationId": "..."}
```

to `{beadle.gui.url}/api/callbacks/protech` with the service token, `X-Beadle-User: system` and the
`X-Correlation-Id` of the user action that last raised, read or acted on the change. The event is `STATE_CHANGED`
when the change reached another state, otherwise `APPROVAL_CHANGED` when an approval of the change or of a task was
requested or given, otherwise `TASK_CHANGED` when a task went in progress; one callback covers the change, since
Beadle-GUI reads all of it again. The change counts as reported only once Beadle-GUI answered `2xx`: a callback it
does not take is logged and sent again at the next look, and the ticker never fails a request. What Beadle-GUI did
itself (a task created, an update, a reminder) is no news to it and is not called back. A closed change is no
longer followed. The live poll compares ProTech's `sys_updated_on` with the clock of Beadle-ProTech, so the two
clocks must agree within the minute of margin.

### Call log

`BEADLE_PROTECH_CALL_LOG` holds one row per call to ProTech (the demo included) or Beadle-GUI: when, the user
(`X-Beadle-User`, or `system` for the ticker), the correlation id, the system (`PROTECH` or `GUI`), the operation
(the method of the port: `raise`, `createTask`, `read`, `update`, `remind`, `find`, `activity`, `notify`), its
target (what was created first, then the change, task, role or lookup the call was about), `SUCCESS` or `FAILURE`
with the message, and the duration. A look at the activity that found nothing is left out, so that the ticks do
not flood the log. The row is written in its own transaction, and a call log that cannot be written never fails
the call.

## Repository layout

```
settings.gradle, build.gradle      one Spring Boot module: whether the BBH Nexus is reachable, the dependencies, the test suites
src/main/java                      the service, package com.bbh.itss.beadle.protech (hexagonal: adapter, application, domain, config)
src/main/resources                 application.yml with the profiles, the Liquibase changelog (call log, followed changes)
src/test, src/testFixtures         the Spock specs and the fixtures they share
src/regressionTest                 the API end to end on H2 with the demo ProTech and a stand-in Beadle-GUI
deploy/openshift/beadle-protech    the Kustomize base and the rd, qc and prod overlays
Dockerfile                         the container image, on the Red Hat UBI 9 OpenJDK 21 runtime
docs/CONTRACTS.md                  the contract between Beadle-GUI, Beadle-Internal and Beadle-ProTech
docs/PROTECH-MAPPING.md            the fields of a change in ProTech and the Table API calls
```

The layers: `adapter/in/web` (the controllers and the error handling), `adapter/in/schedule` (the ticker),
`adapter/out/protech` (the demo ProTech and the live `ServiceNowTableApiAdapter`), `adapter/out/gui` (the
callbacks), `adapter/out/persistence` (the call log and the followed changes), `application/<area>` (the use cases
behind `port/in`, the ports to the outside in `port/out`), `domain/<area>` (records and rules, no framework) and
`config` (the service token filter, transactions, call log, scheduling). `ArchitectureSpec` keeps them apart.

## Build

Needs Java 21 on the `PATH`; the Gradle wrapper downloads Gradle 8.14 from the BBH Nexus inside BBH and from
services.gradle.org outside (`settings.gradle` makes one `HEAD` request to the BBH Nexus to find out where it is;
`-PbbhNetwork=false` or `true` overrides the check).

```bash
./gradlew build                 # the jar, every suite and the coverage gate (60% of lines)
./gradlew bootJar               # build/libs/beadle-protech-<version>.jar
./gradlew test                  # the unit specs
./gradlew regressionTest        # the API end to end on H2 with the demo ProTech
./gradlew publish -Pnexus.snapshotsUrl=<url>   # the jar to the BBH Nexus (bbhNexusUsername and bbhNexusPassword as Gradle properties)
./gradlew bootJar -PartifactVersion=1.0.0
```

## Configuration

Every setting comes from an environment variable; OpenShift fills them from the ConfigMap `beadle-protech-config`
and the secrets below. While `PROTECH_URL` is blank the demo ProTech plays.

| Property | Variable | Default | What |
|---|---|---|---|
| `beadle.services.token` | `BEADLE_SERVICE_TOKEN` | blank: every call is refused | the token shared by the three Beadle services, checked on every call and sent with the callbacks |
| `beadle.gui.url` | `BEADLE_GUI_URL` | `http://localhost:8081` | where Beadle-GUI takes the callbacks |
| `beadle.protech.url` | `PROTECH_URL` | blank: demo | ProTech, for example `https://bbh.service-now.com` |
| `beadle.protech.username` | `PROTECH_USERNAME` | blank | the Beadle service account in ProTech (basic authentication) |
| `beadle.protech.password` | `PROTECH_PASSWORD` | blank | its password |
| `beadle.protech.poll` | `PROTECH_POLL` | `PT1M` | how often the live ProTech is asked what moved |
| `beadle.protech.tick` | `PROTECH_TICK` | `PT10S` | how often the ticker looks (the demo ProTech at every tick, the live one once the poll interval passed) |
| `beadle.protech.demo.apply-delay` | `PROTECH_DEMO_APPLY_DELAY` | `PT3S` | how long the demo ProTech takes to apply an update |
| `beadle.protech.fields.*` | | the ServiceNow base system | the tables, fields, state values and lookup tables of ProTech, one property each; see [docs/PROTECH-MAPPING.md](docs/PROTECH-MAPPING.md) |

The other variables: `PORT` (`8083`), `SPRING_PROFILES_ACTIVE` (`local`), `BEADLE_PROTECH_DATA_DIR` (the H2 folder
of the `local` profile, `~/bbh-devsecops/beadle-protech`), `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and
`DB_POOL_SIZE` (the database). Every client connects within 5 seconds and reads within 30
(`spring.http.clients.*`). Tokens and passwords come only from the environment; none is written to the repository
or the log.

## Running locally

```bash
export BEADLE_SERVICE_TOKEN=<the token Beadle-GUI sends>
./gradlew bootRun               # http://localhost:8083, profile local
curl -H "Authorization: Bearer $BEADLE_SERVICE_TOKEN" http://localhost:8083/api/protech/status
```

The `local` profile (the default) keeps an H2 database in `~/bbh-devsecops/beadle-protech`
(`BEADLE_PROTECH_DATA_DIR` moves it) and uses the demo ProTech: a change it raises moves through the approval
stages two minutes apart, its tasks are approved by the people of their assignment group, an update shows after
the apply delay, and the lookups answer a fixed catalogue of BBH people, groups, products and records. The demo
keeps its changes in memory: after a restart it takes a change back from the copy Beadle-GUI reads. The real
ProTech is connected by setting `PROTECH_URL`, `PROTECH_USERNAME` and `PROTECH_PASSWORD`. Beadle-GUI is expected on
`http://localhost:8081` for the callbacks; while it is not running, the callbacks are logged as failed and sent
again on the next tick. In VS Code the tasks `gradle: bootRun`, `gradle: test`, `gradle: regressionTest` and
`gradle: build` do the same.

## Profiles

| Profile | Database | ProTech |
|---------|----------|---------|
| `local` (default) | H2 file in Oracle mode | demo, unless `PROTECH_URL` is set |
| `rd`, `qc`, `prod` | Oracle from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (`DB_POOL_SIZE` sizes the pool) | the real one when `PROTECH_URL` is set |

`rd`, `qc` and `prod` each switch on the `oracle` profile. Liquibase updates the schema on every start and
Hibernate validates it on Oracle. `rd` logs at `DEBUG`; `rd` and `qc` show the health details.

## Containers and OpenShift

```bash
./gradlew bootJar
docker build -t beadle-protech:0.1.0-SNAPSHOT .
docker run --rm -p 8083:8083 -e BEADLE_SERVICE_TOKEN=<token> -e BEADLE_GUI_URL=http://host.docker.internal:8081 \
  beadle-protech:0.1.0-SNAPSHOT
```

The image runs as user 185, listens on 8083 and keeps the H2 database of the `local` profile in the volume
`/application/data`.

The manifests in `deploy/openshift/beadle-protech` are a Kustomize base (Deployment, Service, Route and the
ConfigMap `beadle-protech-config` with the profile, `BEADLE_GUI_URL=http://beadle:8081`, `PROTECH_URL` and
`PROTECH_POLL`) with one overlay per environment that sets its profile. The Deployment reads the secrets
`beadle-protech-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and `beadle-protech-services`
(`BEADLE_SERVICE_TOKEN`), and the optional `beadle-protech-servicenow` (`PROTECH_URL`, `PROTECH_USERNAME`,
`PROTECH_PASSWORD`; a secret wins over the ConfigMap); its probes are `/actuator/health/liveness` and
`/actuator/health/readiness`.

```bash
oc create secret generic beadle-protech-db --from-literal=DB_URL='jdbc:oracle:thin:@//<host>:1521/<service>' \
  --from-literal=DB_USERNAME='<user>' --from-literal=DB_PASSWORD='<password>'
oc create secret generic beadle-protech-services --from-literal=BEADLE_SERVICE_TOKEN='<token>'
oc create secret generic beadle-protech-servicenow --from-literal=PROTECH_URL='https://bbh.service-now.com' \
  --from-literal=PROTECH_USERNAME='<user>' --from-literal=PROTECH_PASSWORD='<password>'
cd deploy/openshift/beadle-protech/overlays/rd
kustomize edit set image beadle-protech=<registry>/<project>/beadle-protech:0.1.0-SNAPSHOT
oc apply -k .
```

Run one replica: the ticker runs in every replica and each would send the callbacks. During a rolling update the
old and the new pod may both send one; Beadle-GUI reads the change again on each, so a repeated callback does no
harm. The demo ProTech keeps its changes in memory, one copy per pod.

## Tests

| Suite | What |
|-------|------|
| unit (`src/test`) | domain rules, use cases, the controllers through MockMvc (including `401` without or with a wrong token), the live adapter against `MockRestServiceServer`, the demo ProTech, the callback client, the ticker, the call log interceptor, the Liquibase change sets on H2, the profiles and the architecture rules; JaCoCo fails `build` below 60% line coverage (`-Pcoverage.minimum=`) |
| regression (`src/regressionTest`) | the API end to end on H2 in Oracle mode with the demo ProTech and a clock the specs move: the status reporting `demo` and the `401` of a call without the token, a change raised with its tasks and read back through the approval stages to implementation, an update applied after the delay, reminders, refusals, lookups, the call log and the followed changes filling up, and the callbacks for every state, approval and task move (and their retry) taken by a stand-in Beadle-GUI on `MockRestServiceServer` |

`ArchitectureSpec` checks the hexagonal layers: the domain and the application use no framework, adapters in and
out stay apart, controllers live in `adapter/in/web`, persistence types in `adapter/out/persistence`, inbound
adapters do not reach the out ports, only the ProTech and Beadle-GUI adapters use an HTTP client, every use case
implements an in port and calls no other, and no package depends on itself in a cycle.
