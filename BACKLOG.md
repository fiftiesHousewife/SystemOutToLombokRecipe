# Backlog

## Shipped

- **0.3** — version-catalog-aware `SystemOutToLombokLog4jRecipeCatalog` composition.
- **0.4** — catalog auto-wired into `build.gradle.kts`; JUL conversion; manual-Log4j2-logger migration; production-ready `log4j2.xml` + console-only `log4j2-test.xml`; pre-release `SMOKE_TEST.md`.
- **0.5** — pivot to `@Slf4j` + SLF4J API + Log4j2 backend (via `log4j-slf4j2-impl`); unified catalog auto-detect in a single `SystemOutToSlf4jRecipe`; JUL field and import cleaned up after call conversion; deps pinned to explicit versions; recipe family renamed accordingly (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `AddSlf4jDependencies`, `AddLombokDependency`).
- **0.6** — per-module Lombok classpath gating (`requireLombokOnClasspath` option on the annotation-adding recipes, wired through the `*NoDeps` variants via a new `JavaTransformsClasspathGated` composition); Groovy DSL support for `UseCatalogReferenceForDependency` (paren-less idiom preserved via `JContainer.withElements` + manual `J.FieldAccess` + `OmitParentheses` marker copy); 14-cell project-shape matrix (`KotlinDslMatrixTest` + `GroovyDslMatrixTest`) covering single-module × multi-module × `build-logic` × `buildSrc` × catalog × inline × `gradle.properties`-interpolated; four new multi-module / build-logic smoke-test templates in `SMOKE_TEST.md` §2a; new `MigrateToSlf4jRecipe` (+ `NoDeps`) composing `ConvertManualLoggerToSlf4j` ahead of the standard `JavaTransforms` chain so mixed-pattern codebases need only one rewriteRun; `AddDependency` same-group dedup-ordering quirk documented inline in the YAML; `build.gradle.kts` post-rewrite formatting quirk documented in README troubleshooting.

- **0.7** — two real bug fixes for users:
  - **`JavaTransformsClasspathGated` now gates every leaf, not just the annotation-add.** Previously the composition only skipped `@Slf4j` in Lombok-less modules — `SystemOutToSlf4j` / `PrintStackTraceToLog` / `JulToSlf4j` ran unconditionally and rewrote `System.out.println(...)` to `log.info(...)` against a non-existent `log` field, producing uncompilable Java. `requireLombokOnClasspath` is now an `@Option` on all four leaves and the composition wires `true` everywhere. Affects `MigrateToSlf4jRecipeNoDeps` and `SystemOutToSlf4jRecipeNoDeps` users.
  - **`ConvertManualLoggerToSlf4jRecipe` now adds `slf4j-api` and the `log4j-slf4j2-impl` bridge.** Previously it added only Lombok, so post-rewrite Java imported `org.slf4j.Logger` (from `@Slf4j` expansion) against an empty classpath. Fixed by replacing `AddLombokDependency` with `AddSlf4jDependencies` in the YAML.
  
  Plus the infrastructure that surfaced the second bug: the new `withToolingApi()` integration harness (separate `integrationTest` source set, JDK 21 launcher to host the embedded Gradle daemon) with five end-to-end test classes against real Maven Central resolution, and the `./gradlew smokeTest` runner (separate `smokeTest` source set, scaffolds a fresh /tmp Gradle project per cell, drives nested `rewriteRun` + `compileJava` via `ProcessBuilder`, 9-cell single-module matrix). `publishAndReleaseToMavenCentral` (and the related Maven Central publish tasks) now `dependsOn("smokeTest")` so the pre-publish gate is structural, not operator discipline. CI workflow updated to install JDK 25 alongside JDK 21 (the clean-code plugin needs the former, the integration + smoke tasks need the latter as a launcher).

## Active

- **GitHub Packages publish** — the attempt during 0.1 got 403 because the ambient `GITHUB_TOKEN` lacks `write:packages`. Maven Central is doing the heavy lifting so this is a belt-and-braces extra, but still nominally open if we want the mirror.

- **Expand integration coverage further** — currently covered: `AddLombokDependency` (inline + catalog), `AddSlf4jDependencies` (inline dedup-ordering regression guard + catalog seeding), `MigrateToSlf4jRecipe` (mixed-pattern end-to-end including XML seeding), `CreateLog4j2Config` (fresh project + `overwriteExisting=false` idempotency), `JavaTransformsClasspathGated` negative case. Still bootstrap-only: `SystemOutToSlf4jRecipe` (likely covered transitively by `MigrateToSlf4jRecipe`); multi-module / `includeBuild` shapes (the harness only models a single `forProjectDirectory`).

- **Smoke-test automation Phase 2** — Phase 1 covers single-module §2 only; the Maven Central publish gate is in place via Phase 3. Still bootstrap-only: §2a Templates A–F (multi-module Kotlin + Groovy DSL, `include` build-logic, `includeBuild` composite); §3 mavenLocal coordinates round-trip across release-shaped consumer projects. Cost: ~1–1.5 days for §2a (each template has its own scaffolder), ~1 hr for §3.

- **Re-enable Java 25 in integration tests** — bisect performed 2026-05-02 found the catalog regression is a **Gradle 9.x behaviour change**, not an `openrewrite-core` regression:
  - `8.81.2` + Gradle **8.14.3** + JDK 21 launcher: ✅ both inline AND catalog tests pass.
  - `8.81.2` + Gradle **9.4.1** + JDK 25: ❌ catalog test fails — `AddDependency` makes no changes to `build.gradle.kts` when `gradle/libs.versions.toml` is present, leaving the dependencies block absent. Inline test still passes.
  - `8.81.1` + Gradle 9.4.1: rejects `-b` flag. (`isGradle9OrLater` gate landed in 8.81.2, so 8.81.2 is the *first* `openrewrite-core` that runs at all on Gradle 9.)
  - `8.82.0-SNAPSHOT`: only `org.openrewrite.gradle.tooling:model` is published to OSSRH snapshots; the rest of `org.openrewrite:rewrite-*` are not, so the snapshot can't be tested without a different resolution path.
  
  Since 8.81.2 is the first version that runs on Gradle 9 *at all*, there is no earlier "good" version to bisect to. The catalog scenario diverges between Gradle 8.x and Gradle 9.x specifically — either the Tooling API in Gradle 9 returns a `GradleProject` model that confuses `AddDependency`'s catalog-detection branch, or `rewrite-gradle`'s build-script editing assumes a Gradle 8 AST shape. Path forward: file an issue against `openrewrite/rewrite-gradle` with the minimal repro (8.81.2 + 9.4.1 catalog scenario), wait for fix, or temporarily downgrade in-process integration tests to Gradle 8.x while production users on Gradle 9 are covered by the manual `SMOKE_TEST.md`. Current state: harness stays on JDK 21 + Gradle 8.x default until upstream fixes Gradle 9 catalog handling.

## Parked (re-open on request)

- **Auto version-detection at recipe runtime** — for now we pin Lombok / SLF4J / Log4j2 versions explicitly and check Maven Central by hand pre-release (`SMOKE_TEST.md`). Full runtime-detection would be a custom recipe that queries Central when it runs.

- **Log-and-throw cleanup** — detect `log.error(msg, e); throw …;` and drop the log so the exception isn't double-reported. Nontrivial because it needs to reason about whether the throw reaches a boundary where something else logs it. Explicitly deferred.
