# Backlog

## 0.3 — Shipped

- **Catalog entries populated automatically**: `AddVersionCatalogEntry` recipe using `rewrite-toml` LST adds entries to `[versions]` and `[libraries]` when they aren't already present.
- **`SystemOutToLombokLog4jRecipeCatalog` composition**: runs the catalog entries + Java transforms + `log4j2.xml` creation in one shot. Verified end-to-end against a sample Gradle catalog project.

## 0.4 — In progress

- ✅ **`log4j2.xml` review**: ERROR now routes to stderr via a `ThresholdFilter`; stdout appender only handles non-error levels; timestamps include the date. The XML template is factored out into a shared `CreateLog4j2Config` sub-recipe, and the Java transforms into `JavaTransforms` — three top-level recipes compose these cleanly.
- ✅ **`java.util.logging` fixer**: `JulToLombokLog4j` recipe maps `severe/warning/info/config/fine/finer/finest` to the corresponding Lombok `log.xxx` calls. `AddLombokLog4j2Annotation` was extended to trigger on JUL usage as well, so JUL-using classes get `@Log4j2` when the default composition runs.
- ✅ **Manual-logger → Lombok migration**: `ConvertManualLog4j2ToLombok` recipe for codebases that already use Log4j2 with hand-rolled `private static final Logger log = LogManager.getLogger(...)` fields. Adds `@Log4j2`, removes the field, renames `logger`/`LOG`/`LOGGER` references to `log`, and cleans up now-unused Logger/LogManager imports. Three YAML variants (`ConvertManualLog4j2ToLombokRecipe` inline deps, NoDeps, Catalog). Verified end-to-end against a sample project; transformed code compiles.
- ⏳ **Auto-wire `build.gradle.kts`**: after populating the catalog, also insert `compileOnly(libs.lombok)` etc. into the `dependencies { ... }` block. Requires a Kotlin DSL-aware visitor (Gradle plugin parses `build.gradle.kts` as `K.CompilationUnit`, not PlainText).

## Future

- **`java.util.logging` fixer**: transform `Logger.getLogger(...).info(...)` / `.severe(...)` / etc. to Lombok `@Log4j2` log calls.
- **Non-Lombok variant**: for projects that don't use Lombok, generate a plain `private static final Logger log = LogManager.getLogger(ClassName.class);` field instead of the `@Log4j2` annotation.
- **Review `log4j2.xml` output**: confirm the generated configuration is sensible for common production setups (e.g. file appender, pattern, rolling policy). Currently only a console appender.
- **Log-and-throw pattern**: detect `log.error(msg, e); throw ...;` and remove the log call so the exception isn't double-reported. Needs to consider exception swallowing and boundary handling — decide where the error is actually logged (boundary) vs. rethrown silently (internal).
- **Real-world smoke test**: publish + consume in a small external project, inspect diff, before tagging releases.
