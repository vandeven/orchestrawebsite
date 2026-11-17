# Orchestra website

A website for an orchestra (a band and a choir): public information, and a members area with sheet music, recordings and lyrics, managed by admins.

- **Specification:** [`docs/PLAN.md`](docs/PLAN.md) is the starting point; every feature has its own document in `docs/`.
- **Build order and definition of done:** [`docs/roadmap.md`](docs/roadmap.md).
- **Deploy and maintain from scratch:** [`docs/manual.md`](docs/manual.md).
- **How to work on it:** [`CONTRIBUTING.md`](CONTRIBUTING.md).
- **Instructions for admins and the owner:** [`docs/instructions.md`](docs/instructions.md). **Emergencies:** [`docs/runbook.md`](docs/runbook.md).
- **Demo:** [`demo/`](demo/README.md) is a clickable front-end prototype of the flow. It is not the specification and is not the real application.

## Status
Specification phase. The application, the pipeline and `infra/` do not exist yet; they are built following the roadmap.

## Run it locally (when the application exists)
- `./mvnw quarkus:dev` starts the application with the Firestore and Cloud Storage emulators and fake secrets from the example configuration file.
- The seed command fills the emulators with fake users (one per role), songs with tiny dummy files, texts and images.
- `./mvnw verify` runs the Enforcer rules (pinned plugin versions, Java and Maven versions), the tests, the architecture tests, the formatting check (Spotless) and the coverage check (at least 80% of lines, JaCoCo).
- `./mvnw spotless:apply` fixes the formatting.

Details are added to this README together with the first code.
