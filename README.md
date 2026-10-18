# Beadle-ProTech

Beadle-ProTech connects Beadle to ProTech, BBH's ServiceNow instance. For Beadle-GUI it creates production changes as
ProTech change requests with their tasks, reads them back as they move through the approval workflow, updates them,
reminds their approvers, and looks up people, business services, assignment groups, releases, configuration items,
incidents, problems and clients. It signs in with Beadle's generic service account, logs every call it makes, and
calls Beadle-GUI back when ProTech moves a change.

## How it fits with the other two services

```
Beadle-GUI (8081) ──REST──> Beadle-ProTech (8083) ──Table API──> ProTech
      ^                            │  ^
      └─── callback: change moved ─┘  └── webhook: ProTech reports a moved change (optional)
                                      Beadle-Internal (8082) talks to Jira, CyberTrack, Confluence and qTest
```

Only Beadle-GUI calls the API, and ProTech may call the webhook. Every call carries
`Authorization: Bearer <BEADLE_SERVICE_TOKEN>`, the signed-in user in `X-Beadle-User` and the user action in
`X-Correlation-Id` (at most 64 characters). The webhook also accepts its own `PROTECH_WEBHOOK_TOKEN`, which opens
nothing else. The endpoints, bodies, headers and the callback are specified in [docs/CONTRACTS.md](docs/CONTRACTS.md),
which is the same file in all three repositories. [docs/PROTECH-MAPPING.md](docs/PROTECH-MAPPING.md) lists the
ProTech tables, fields and Table API calls behind every endpoint.

## Architecture

Hexagonal, with Spring MVC controllers as the way in. `ArchitectureSpec` enforces the layers.

```
com.bbh.itss.beadle.protech
├── domain/change, calllog     records and rules, no framework
├── application/<area>         use cases behind port/in, ports to the outside in port/out
├── adapter/in/web             controllers, the webhook, error handling, the service token filter
├── adapter/in/schedule        the ticker that looks for moved changes
├── adapter/out/protech        LiveProTechAdapter (Table API) and the demo ProTech for the laptop
├── adapter/out/gui            the callbacks to Beadle-GUI
├── adapter/out/persistence    JPA: call log, followed changes
└── config                     Spring wiring and properties
```

Every change created or read through the API is recorded in `BEADLE_PROTECH_CHANGE` with the state Beadle-GUI knows.
The ticker asks ProTech every `PROTECH_POLL` what moved, and the webhook triggers the same check at once. For each
change that moved, one callback goes to `{BEADLE_GUI_URL}/api/callbacks/protech` with the event `STATE_CHANGED`,
`APPROVAL_CHANGED` or `TASK_CHANGED`. A callback that Beadle-GUI does not accept is sent again at the next check. The
poll compares ProTech's `sys_updated_on` with this service's clock, so both clocks must be kept in sync by NTP.

## Run on a BBH laptop

```bash
export BEADLE_SERVICE_TOKEN=local-$(whoami)     # the same value as for Beadle-GUI
./gradlew bootRun                               # http://localhost:8083, profile local
curl -H "Authorization: Bearer $BEADLE_SERVICE_TOKEN" http://localhost:8083/api/protech/status
```

The `local` profile (the default) uses an H2 file database in Oracle mode in `~/bbh-devsecops/beadle-protech`. It uses
the demo ProTech while `PROTECH_URL` is blank: a change moves through the approval stages two minutes apart, and the
lookups answer from a fixed catalogue of BBH people, groups and products. Set `PROTECH_URL`, `PROTECH_USERNAME` and
`PROTECH_PASSWORD` to use the real ProTech.

## Build and tests

The build uses the BBH Nexus only, so it runs inside the BBH network or on the VPN.

| Task | What | Typical time |
|---|---|---|
| `./gradlew build` | jar, all test suites, coverage gates | about 1 min |
| `./gradlew bootJar` | `build/libs/beadle-protech-<version>.jar` | 10 s |
| `./gradlew test` | unit specs (Spock), JaCoCo gate of 60% of lines | 20 s |
| `./gradlew smokeTest` | probes; a change created, read back and checked through the webhook | 15 s |
| `./gradlew regressionTest` | the change journey and the callbacks, on H2 with the demo ProTech | 20 s |
| `./gradlew performanceTest` | p95 of creating, reading and looking up, each under 500 ms | 15 s |
| `./gradlew jacocoSuitesReport` | combined coverage of the three suites, gate of 30% | part of `build` |

## Configuration

Every setting comes from an environment variable. In OpenShift they come from the ConfigMap `beadle-protech-config`
and the secrets listed under Deployment.

| Property | Variable | Default | Meaning |
|---|---|---|---|
| `beadle.services.token` | `BEADLE_SERVICE_TOKEN` | blank: every call is refused | token shared by the three services |
| `beadle.gui.url` | `BEADLE_GUI_URL` | `http://localhost:8081` | where the callbacks go |
| `beadle.protech.instance-url` | `PROTECH_URL` | blank: demo on a laptop | ProTech's address |
| `beadle.protech.username`, `.password` | `PROTECH_USERNAME`, `PROTECH_PASSWORD` | blank | Beadle's service account (basic authentication) |
| `beadle.protech.webhook-token` | `PROTECH_WEBHOOK_TOKEN` | blank: webhook takes the service token only | token ProTech sends to the webhook |
| `beadle.protech.poll` | `PROTECH_POLL` | `PT1M` (`PT10S` locally) | how often ProTech is asked what moved |
| `beadle.protech.demo.apply-delay` | `PROTECH_DEMO_APPLY_DELAY` | `PT3S` | how long the demo takes to apply an update |
| `beadle.protech.fields.*` | | ServiceNow defaults | ProTech's tables, fields, states and lookup tables; see PROTECH-MAPPING.md |

The property is `instance-url`, not `url`, because Spring would otherwise bind Beadle-GUI's `BEADLE_PROTECH_URL` to
it. Also: `PORT` (`8083`), `SPRING_PROFILES_ACTIVE` (`local`), `BEADLE_PROTECH_DATA_DIR` (H2 folder), `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE`, `LOG_PATH` (`./logs`). Tokens and passwords come only from the
environment.

| Profile | Database | ProTech |
|---|---|---|
| `local` (default) | H2 file in Oracle mode | demo while `PROTECH_URL` is blank |
| `rd`, `qc`, `prod` | Oracle (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) | the real one only; the service does not start without `PROTECH_URL` |

Liquibase updates the schema at start-up and Hibernate validates it. Logs go to the console and to a rolling file in
`LOG_PATH`.

## Deployment

```bash
./gradlew bootJar && docker build -t beadle-protech:0.1.0-SNAPSHOT .
```

The image runs as user 185 on port 8083, keeps the local H2 database in `/application/data` and writes logs to
`/application/logs`.

All three Beadle services run in one OpenShift project, one pod each. `deploy/openshift/beadle-protech` holds a
Kustomize base (Deployment, Service, ConfigMap) and the overlays `rd`, `qc` and `prod`, which set the profile. There is
no Route: Beadle-GUI reaches the service at `http://beadle-protech:8083`. Once ProTech calls the webhook, an overlay
adds `components/webhook-route`, which publishes `/api/protech/webhook` alone.

The Deployment reads the secrets `beadle-protech-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`), `beadle-services`
(`BEADLE_SERVICE_TOKEN`, shared by the three services) and `beadle-protech-account` (`PROTECH_URL`,
`PROTECH_USERNAME`, `PROTECH_PASSWORD`, `PROTECH_WEBHOOK_TOKEN`). [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) walks through
a full deployment of the three services.

## Known gaps

- The connection to the real ProTech has not been tried yet; its address, service account and webhook token must be
  filled in for each environment, and the field names in PROTECH-MAPPING.md checked against BBH's ProTech.
- The webhook needs a business rule in ProTech; without it, changes are found by the poll only.
- Three Oracle identifiers are longer than 30 characters, which needs Oracle 12.2 or later (BBH runs 19c, to be
  confirmed).
- The base image tag in the Dockerfile must be set to the BBH-approved UBI 9 OpenJDK 21 image, the same in all three
  repositories.
