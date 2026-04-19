# Backlog

## 0.3 — In progress

- **Catalog-aware dependency addition**: detect `gradle/libs.versions.toml` and write dependencies there instead of inline in `build.gradle.kts`. Fall back to inline when no catalog exists.
  - ✅ `AddVersionCatalogEntry` recipe — adds `[versions]` + `[libraries]` rows to `gradle/libs.versions.toml`. Uses `rewrite-toml` LST. End-to-end tested against a real sample project.
  - ⏳ Conditional application: skip when no catalog is present so the recipe is safe to include unconditionally in compositions.
  - ⏳ `build.gradle.kts` side: add `configuration(libs.xxx)` references instead of inline `"group:artifact:version"`.
  - ⏳ Compose into `SystemOutToLombokLog4jRecipeCatalog` — full catalog-aware flow.

## Future

- **`java.util.logging` fixer**: transform `Logger.getLogger(...).info(...)` / `.severe(...)` / etc. to Lombok `@Log4j2` log calls.
- **Non-Lombok variant**: for projects that don't use Lombok, generate a plain `private static final Logger log = LogManager.getLogger(ClassName.class);` field instead of the `@Log4j2` annotation.
- **Review `log4j2.xml` output**: confirm the generated configuration is sensible for common production setups (e.g. file appender, pattern, rolling policy). Currently only a console appender.
- **Log-and-throw pattern**: detect `log.error(msg, e); throw ...;` and remove the log call so the exception isn't double-reported. Needs to consider exception swallowing and boundary handling — decide where the error is actually logged (boundary) vs. rethrown silently (internal).
- **Real-world smoke test**: publish + consume in a small external project, inspect diff, before tagging releases.
