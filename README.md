# Beadle-ProTech

Beadle-ProTech is the service between Beadle and ProTech, BBH's ServiceNow. It raises the production changes of
Beadle-GUI as ProTech change requests with their tasks, reads them back as they move through the approval
workflow, updates them, reminds their approvers and looks up the people, departments, assignment groups, releases,
configuration items, incidents, problems and clients of ProTech. It signs in to ProTech with the generic service
account of Beadle, keeps a call log of every call it makes and calls Beadle-GUI back when a change it follows
moves on. Without a ProTech address it runs a demo ProTech that drives the workflow by the clock.

[docs/CONTRACTS.md](docs/CONTRACTS.md) is the contract between the three Beadle repositories (Beadle-GUI,
Beadle-Internal and Beadle-ProTech): the REST API of this service, the headers every call carries and the callback
to Beadle-GUI. The code follows it to the letter.

## Repository layout

```
settings.gradle, build.gradle   one Spring Boot module: whether the BBH Nexus is reachable, the dependencies, the test suites
src/main/java                   the service, package com.bbh.itss.beadle.protech (hexagonal: adapter, application, domain, config)
src/main/resources              application.yml with the profiles and the Liquibase changelog
src/test, src/testFixtures      the Spock specs and the fixtures they share
deploy/openshift/beadle-protech    the Kustomize base and the rd, qc and prod overlays
Dockerfile                      the container image, on the Red Hat UBI 9 OpenJDK 21 runtime
```

## Build

Needs Java 21 on the `PATH`; the Gradle wrapper downloads Gradle 8.14 from the BBH Nexus inside BBH and from
services.gradle.org outside (`settings.gradle` makes one `HEAD` request to the BBH Nexus to find out where it is;
`-PbbhNetwork=false` or `true` overrides the check).

```bash
./gradlew build                 # the jar, every suite and the coverage gate (60% of lines)
./gradlew bootJar               # build/libs/beadle-protech-<version>.jar
./gradlew test                  # the unit specs
./gradlew publish -Pnexus.snapshotsUrl=<url>   # the jar to the BBH Nexus (bbhNexusUsername and bbhNexusPassword as Gradle properties)
./gradlew bootJar -PartifactVersion=1.0.0
```

## Running locally

```bash
export BEADLE_SERVICE_TOKEN=<the token Beadle-GUI sends>
./gradlew bootRun               # http://localhost:8083, profile local
```

The `local` profile (the default) keeps an H2 database in `~/bbh-devsecops/beadle-protech`
(`BEADLE_PROTECH_DATA_DIR` moves it) and uses the demo ProTech; the real one is connected by setting
`PROTECH_URL`, `PROTECH_USERNAME` and `PROTECH_PASSWORD`.

## Profiles

| Profile | Database | ProTech |
|---------|----------|---------|
| `local` (default) | H2 file in Oracle mode | demo, unless `PROTECH_URL` is set |
| `rd`, `qc`, `prod` | Oracle from `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (`DB_POOL_SIZE` sizes the pool) | the real one when `PROTECH_URL` is set |

Liquibase updates the schema on every start and Hibernate validates it.

## Containers and OpenShift

```bash
./gradlew bootJar
docker build -t beadle-protech:0.1.0-SNAPSHOT .
docker run --rm -p 8083:8083 -e BEADLE_SERVICE_TOKEN=<token> beadle-protech:0.1.0-SNAPSHOT
```

The manifests in `deploy/openshift/beadle-protech` are a Kustomize base (Deployment, Service, Route and the
ConfigMap `beadle-protech-config`) with one overlay per environment. The Deployment reads the secrets
`beadle-protech-db` (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) and `beadle-protech-services`
(`BEADLE_SERVICE_TOKEN`), and the optional `beadle-protech-servicenow` (`PROTECH_URL`, `PROTECH_USERNAME`,
`PROTECH_PASSWORD`):

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

## Tests

| Suite | What |
|-------|------|
| unit (`src/test`) | domain rules, the demo ProTech, the configuration and the architecture rules; JaCoCo fails `build` below 60% line coverage (`-Pcoverage.minimum=`) |
