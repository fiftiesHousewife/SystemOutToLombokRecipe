# Backlog

## 0.3 — Shipped

- **Catalog entries populated automatically**: `AddVersionCatalogEntry` recipe using `rewrite-toml` LST adds entries to `[versions]` and `[libraries]` when they aren't already present.
- **`SystemOutToLombokLog4jRecipeCatalog` composition**: runs the catalog entries + Java transforms + `log4j2.xml` creation in one shot. Verified end-to-end against a sample Gradle catalog project.

## 0.4 — Ready to ship

- ✅ **`log4j2.xml` review**: main config now includes a `RollingFile` appender (daily + 10 MB gzip rollover, 10 files kept) on top of stdout/stderr routing. A companion `src/test/resources/log4j2-test.xml` (console only) prevents tests from writing into `./logs/`. The XML templates are factored into a shared `CreateLog4j2Config` sub-recipe, and the Java transforms into `JavaTransforms` — three top-level recipes compose these cleanly.
- ✅ **`java.util.logging` fixer**: `JulToLombokLog4j` recipe maps `severe/warning/info/config/fine/finer/finest` to the corresponding Lombok `log.xxx` calls. `AddLombokLog4j2Annotation` was extended to trigger on JUL usage as well, so JUL-using classes get `@Log4j2` when the default composition runs.
- ✅ **Manual-logger → Lombok migration**: `ConvertManualLog4j2ToLombok` recipe for codebases that already use Log4j2 with hand-rolled `private static final Logger log = LogManager.getLogger(...)` fields. Adds `@Log4j2`, removes the field, renames `logger`/`LOG`/`LOGGER` references to `log`, and cleans up now-unused Logger/LogManager imports. Three YAML variants (`ConvertManualLog4j2ToLombokRecipe` inline deps, NoDeps, Catalog). Verified end-to-end against a sample project; transformed code compiles.
- ✅ **Auto-wire `build.gradle.kts`**: the Catalog variant now fills in `compileOnly(libs.lombok)`, `annotationProcessor(libs.lombok)`, `implementation(libs.log4jApi)`, `runtimeOnly(libs.log4jCore)` automatically via `org.openrewrite.gradle.AddDependency` followed by the custom `UseCatalogReferenceForDependency` post-processor. Verified end-to-end — transformed catalog project compiles from scratch.
- ✅ **Pre-release smoke-test checklist** (`SMOKE_TEST.md`): codifies the "build jar → fresh sample project → `rewriteRun` → `compileJava`" flow for each variant as a documented gate.

## Future

- **Dependabot for pinned recipe versions**: our recipe YAML hard-codes `1.18.44` (Lombok) and `2.25.4` (log4j2). Dependabot/Renovate doesn't watch string literals in YAML by default — a small custom manifest or a GitHub Action that queries Maven Central and opens a bump PR would keep us honest without a release-time ritual.

- **Log-and-throw pattern**: detect `log.error(msg, e); throw ...;` and remove the log call so the exception isn't double-reported. Needs to consider exception swallowing and boundary handling — decide where the error is actually logged (boundary) vs. rethrown silently (internal).
- **SLF4J path**: today we assume Log4j2 is the target. A parallel family of recipes for SLF4J + Logback (`@Slf4j` annotation, `logback.xml` instead of `log4j2.xml`) would cover the other common logging stack.
- **Groovy DSL coverage**: `build.gradle` (Groovy) isn't tested. The Kotlin DSL path is verified; extending to `.gradle` would widen the audience.
- **Local code-quality sweep**: next session's focus per pippa — tighten this repo's own internals (naming, comments, dead code, test structure) now that the feature set is filling out.
