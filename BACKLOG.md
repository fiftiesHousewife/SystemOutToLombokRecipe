# Backlog

## 0.3 — Shipped

- **Catalog entries populated automatically**: `AddVersionCatalogEntry` recipe using `rewrite-toml` LST adds entries to `[versions]` and `[libraries]` when they aren't already present.
- **`SystemOutToLombokLog4jRecipeCatalog` composition**: runs the catalog entries + Java transforms + `log4j2.xml` creation in one shot. Verified end-to-end against a sample Gradle catalog project.

## 0.4 — Roadmap

- **Auto-wire `build.gradle.kts`**: after populating the catalog, also insert `compileOnly(libs.lombok)` / `annotationProcessor(libs.lombok)` / `implementation(libs.log4jApi)` / `runtimeOnly(libs.log4jCore)` into the `dependencies { ... }` block. Requires a Kotlin DSL-aware visitor (the Gradle plugin parses `build.gradle.kts` as `K.CompilationUnit`, not PlainText). OpenRewrite's `MigrateDependenciesToVersionCatalog` exists but doesn't compose cleanly after `AddDependency` in a scanning context — needs investigation or a custom `KotlinIsoVisitor` recipe.

## Future

- **`java.util.logging` fixer**: transform `Logger.getLogger(...).info(...)` / `.severe(...)` / etc. to Lombok `@Log4j2` log calls.
- **Non-Lombok variant**: for projects that don't use Lombok, generate a plain `private static final Logger log = LogManager.getLogger(ClassName.class);` field instead of the `@Log4j2` annotation.
- **Review `log4j2.xml` output**: confirm the generated configuration is sensible for common production setups (e.g. file appender, pattern, rolling policy). Currently only a console appender.
- **Log-and-throw pattern**: detect `log.error(msg, e); throw ...;` and remove the log call so the exception isn't double-reported. Needs to consider exception swallowing and boundary handling — decide where the error is actually logged (boundary) vs. rethrown silently (internal).
- **Real-world smoke test**: publish + consume in a small external project, inspect diff, before tagging releases.
