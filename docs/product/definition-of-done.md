# Definition of done

A work item is complete only when every applicable condition below is met.

## Behaviour

- Acceptance criteria are met.
- Success and error behaviour are explicit.
- Financial and security invariants remain true.
- No placeholder implementation remains.
- No unexplained pseudocode remains.
- No required behaviour exists only as a manual instruction.
- The application does not claim to process real money.

## Testing

- Unit tests cover domain rules.
- Integration tests cover database and framework behaviour.
- Architecture tests cover module boundaries.
- End-to-end tests cover completed user journeys.
- Concurrency behaviour is tested when shared state changes.
- Negative authorisation tests exist for protected operations.
- Failure paths are tested.
- Tests have been executed and their actual results observed.
- A phase is not declared complete based on assumed test results.

## Database

- Schema changes use forward-only Flyway migrations.
- Migrations run against a clean database.
- Upgrade behaviour is tested from the previous supported schema.
- Database constraints protect important invariants where practical.
- Rollback or recovery strategy is documented.
- Financial records are not silently edited or deleted.
- Migration files already applied to a shared environment are not modified.

## API

- Request and response schemas are documented.
- Validation errors identify affected fields.
- Business conflicts use stable machine-readable codes.
- Errors use a consistent problem-details format.
- OpenAPI output matches implemented behaviour.
- API examples contain synthetic data only.
- Pagination and input sizes are bounded.

## Security

- Access control is applied at the service boundary.
- Object-level ownership is checked.
- Inputs have explicit size and format limits.
- Secrets and sensitive values are absent from logs.
- Security-relevant actions create audit events.
- Abuse and replay cases are considered.
- Dependency findings are reviewed.
- Suppressed findings have a documented reason.

## Architecture

- Spring Modulith verification passes.
- Cross-module calls use public module APIs or events.
- New major decisions are captured in ADRs.
- Existing accepted ADRs are not silently contradicted.
- Event consumers remain idempotent.
- Strong and eventual consistency boundaries remain documented.

## Code quality

- Formatting passes.
- Static analysis passes.
- Compiler warnings are reviewed.
- No unnecessary duplication is introduced.
- Names communicate domain meaning.
- Public APIs have a clear purpose.
- Monetary values do not use `float` or `double`.

## Delivery

- The clean build passes.
- Relevant Docker configuration validates.
- Documentation is updated.
- The progress ledger is updated.
- Reproduction commands are included.
- The conventional commit accurately describes the change.
- Generated files and credentials are not committed.
- The working tree is reviewed before commit.
