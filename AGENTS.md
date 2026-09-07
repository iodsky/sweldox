# AGENTS.md

Spring Boot 3.5.6 · Java 21 · Gradle · PostgreSQL 16 · Flyway · Spring Security (JWT) · SpringDoc.

## Commands

```bash
# Start local PostgreSQL (+ pgAdmin at :5050); needs .env first
docker compose -f db.compose.yml up -d

# Run app locally (sources .env, then ./gradlew bootRun, profile=local)
./run.sh

# All tests
./gradlew clean test

# Single test class / method
./gradlew test --tests "com.iodsky.mysweldo.benefit.BenefitServiceTest"
./gradlew test --tests "com.iodsky.mysweldo.benefit.BenefitServiceTest.methodName"
```

- Copy `.env.template` → `.env` and fill DB/JWT values before running. `.env` is loaded via `spring.config.import: optional:file:.env[.properties]` — not through Gradle, so new env vars must be added to `.env.template`.
- All endpoints are under `/api` (server context-path). Swagger UI at `/api/swagger-ui.html`, OpenAPI JSON at `/api/docs`.
- CI (`.github/workflows/ci-cd.yml`) runs `./gradlew clean test` on all PRs to `master`/`develop`; on `master` it builds `./gradlew clean bootJar`, builds + pushes the Docker image to GHCR. The SSH deploy step is commented out.

## Architecture

Every domain module under `src/main/java/com/iodsky/mysweldo/<domain>/` follows the same layered pattern:

```
<Domain>Controller.java   # REST endpoints
<Domain>Service.java      # business logic (@Service, @RequiredArgsConstructor)
<Domain>Repository.java   # Spring Data JPA
<Domain>Entity.java       # JPA entity extends BaseModel
<Domain>Request.java      # inbound DTO
<Domain>Dto.java          # outbound DTO
<Domain>Mapper.java       # Entity <-> DTO conversion (also used for Request -> Entity)
```

Domains: `attendance`, `benefit`, `contribution`, `deduction`, `department`, `employee`, `imports`, `leave` (subpackages `credit`/`request`), `overtime`, `pagIbig`, `payroll`, `philhealth`, `position`, `security` (auth/jwt/role/user), `sss`, `tax`.

### Shared infrastructure (`common/`)

- `BaseModel` is the superclass of every entity: provides `createdAt`/`updatedAt`, `createdBy`/`lastModifiedBy` (JPA auditing), `version` (optimistic locking), and `deletedAt` (soft delete). Entities are annotated `@SQLRestriction("deleted_at IS NULL")` so soft-deleted rows are auto-filtered — don't add manual `deleted_at` filters on top.
- Controllers return **DTOs directly** (no envelope). Paginated endpoints return `PageDto<T>` (`content` + `PaginationMeta`). Errors flow through `GlobalExceptionHandler`, which returns an `ErrorResponse` (`timestamp`, `status`, `message`, optional `validationErrors`/`duplicateField`, `path`).
- Errors are raised as `ResponseStatusException` in services; payroll uses `PayrollRunException` (handled separately in `GlobalExceptionHandler`).

### Payroll engine (`payroll/`)

Non-trivial; split into four sub-packages with distinct responsibilities:
- `payroll/calc` — `PayrollCalculator` (@Component) + `PayrollItemAssembler` compose statutory deductions/contributions and line items; also `StatutoryRateSnapshot`.
- `payroll/strategy` — pay-basis strategies (`PayBasisStrategyFactory` selects hourly/daily/monthly) and `StandardPayrollComputationStrategy`. Add a new pay type here.
- `payroll/item` — payroll line items: `PayrollItem`, `PayrollBenefit`, `PayrollDeduction`, `EmployerContribution` entities + service/controller/mapper.
- `payroll/run` — `PayrollRunService` orchestrates full payroll runs across active employees. Run creation is serialized with a PostgreSQL advisory lock (`pg_advisory_xact_lock` via `PayrollRunRepository.acquireRunCreationLock`) so the REGULAR overlap check + insert is atomic; overlapping REGULAR runs return `409 CONFLICT`.

When touching payroll, check the existing strategy classes first — logic is heavily delegated, not centralized.

### Transactions & OSIV

Write methods are annotated `@Transactional` so multi-step mutations commit or roll back atomically rather than relying on OSIV. `spring.jpa.open-in-view: true` is set deliberately and kept on.

### Security

JWT issued at `/auth/login` and `/auth/refresh`. Only `/auth/**`, `/docs/**`, `/swagger-ui/**` are public; everything else requires a valid access token. Both the short-lived access token (`access_token` cookie) and the long-lived refresh token (`jwt` cookie) are set as **httpOnly cookies** by the server and are never exposed to client JS. The `JwtAuthenticationFilter` reads the access token from the cookie first, falling back to the `Authorization: Bearer` header (so Swagger `Authorize` still works). Role-based access is enforced via Spring method security (`@EnableMethodSecurity`).

### Imports / uploads (`imports/`)

CSV imports use **OpenCSV** (no Spring Batch). `ImportController` exposes `/jobs/import-employees`, `/jobs/import-users` (async) and `/jobs/{id}` (status). `AbstractImportService<T>` runs the async pipeline (`@Async("importTaskExecutor")`, see `common/AsyncConfig`): parse with `CsvToBeanBuilder`, persist each row via repository (per-row commit), skip up to 100 failing rows into `import_job_error` (failures returned in the details endpoint), delete the file when done. Status lives in `import_job`/`import_job_error` (created by V5). `EmployeeImportService`/`UserImportService` hold the row→entity mapping + reference-data caches. Employee imports do **not** assign supervisors (HR does that via `PUT /employees/{id}`); the CSV has no `supervisorId` column. File uploads are capped at 10MB and stored under `uploads/` (configured via `import.upload.directory`).

## Database

- Flyway migrations in `src/main/resources/db/migration/` as `V{n}__description.sql`. **Never modify an existing migration** — both profiles run `ddl-auto: validate`, and Flyway checksums will fail. Add a new `V{n+1}__...sql` instead.
- `src/main/resources/db/migration/` is the only schema source of truth. Current: `V1__initial_schema.sql`, `V2__fix_overtime_soft_delete_unique.sql`, `V3__delete_strategy_fixes.sql`, `V4__salary_history.sql`, `V5__replace_batch_with_import_tracking.sql`.

## Spring profiles

| Profile | Activation | DB |
|---------|-----------|----|
| `local` | default via `./run.sh` | `localhost:5432` from `.env` (LOCAL_DB_*) |
| `prod`  | set at CI/CD deploy | Cloud PostgreSQL from `.env` (CLOUD_DB_*) |

## Conventions

- Controllers return DTOs directly (`PageDto<T>` for paginated). Services throw `ResponseStatusException`. Match the existing pattern in the domain you touch rather than inventing a new response shape.
- Tests use JUnit 5 + AssertJ, are named `<Domain>ServiceTest`, and live under `src/test/java/com/iodsky/mysweldo/<domain>/`. Stubs for repository/view interfaces are colocated (e.g. `EmployeeBasicStub`, `AttendanceViewStub`).
