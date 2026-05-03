# Backlog

## Shipped

- **0.3** — version-catalog-aware `SystemOutToLombokLog4jRecipeCatalog` composition.
- **0.4** — catalog auto-wired into `build.gradle.kts`; JUL conversion; manual-Log4j2-logger migration; production-ready `log4j2.xml` + console-only `log4j2-test.xml`; pre-release `SMOKE_TEST.md`.
- **0.5** — pivot to `@Slf4j` + SLF4J API + Log4j2 backend (via `log4j-slf4j2-impl`); unified catalog auto-detect in a single `SystemOutToSlf4jRecipe`; JUL field and import cleaned up after call conversion; deps pinned to explicit versions; recipe family renamed accordingly (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `AddSlf4jDependencies`, `AddLombokDependency`).
- **0.6** — per-module Lombok classpath gating (`requireLombokOnClasspath` option on the annotation-adding recipes, wired through the `*NoDeps` variants via a new `JavaTransformsClasspathGated` composition); Groovy DSL support for `UseCatalogReferenceForDependency` (paren-less idiom preserved via `JContainer.withElements` + manual `J.FieldAccess` + `OmitParentheses` marker copy); 14-cell project-shape matrix (`KotlinDslMatrixTest` + `GroovyDslMatrixTest`) covering single-module × multi-module × `build-logic` × `buildSrc` × catalog × inline × `gradle.properties`-interpolated; four new multi-module / build-logic smoke-test templates in `SMOKE_TEST.md` §2a; new `MigrateToSlf4jRecipe` (+ `NoDeps`) composing `ConvertManualLoggerToSlf4j` ahead of the standard `JavaTransforms` chain so mixed-pattern codebases need only one rewriteRun; `AddDependency` same-group dedup-ordering quirk documented inline in the YAML; `build.gradle.kts` post-rewrite formatting quirk documented in README troubleshooting.

- **0.7** — two real bug fixes for users:
  - **`JavaTransformsClasspathGated` now gates every leaf, not just the annotation-add.** Previously the composition only skipped `@Slf4j` in Lombok-less modules — `SystemOutToSlf4j` / `PrintStackTraceToLog` / `JulToSlf4j` ran unconditionally and rewrote `System.out.println(...)` to `log.info(...)` against a non-existent `log` field, producing uncompilable Java. `requireLombokOnClasspath` is now an `@Option` on all four leaves and the composition wires `true` everywhere. Affects `MigrateToSlf4jRecipeNoDeps` and `SystemOutToSlf4jRecipeNoDeps` users.
  - **`ConvertManualLoggerToSlf4jRecipe` now adds `slf4j-api` and the `log4j-slf4j2-impl` bridge.** Previously it added only Lombok, so post-rewrite Java imported `org.slf4j.Logger` (from `@Slf4j` expansion) against an empty classpath. Fixed by replacing `AddLombokDependency` with `AddSlf4jDependencies` in the YAML.
  
  Plus the infrastructure that surfaced the second bug: the new `withToolingApi()` integration harness (separate `integrationTest` source set, JDK 21 launcher to host the embedded Gradle daemon) with six end-to-end test classes against real Maven Central resolution (the sixth, `SystemOutToSlf4jRecipeIntegrationTest`, closed a coverage gap where the recipe was only exercised transitively via `MigrateToSlf4jRecipe`), and the `./gradlew smokeTest` runner (separate `smokeTest` source set, scaffolds a fresh /tmp Gradle project per cell, drives nested `rewriteRun` + `compileJava` via `ProcessBuilder`). The runner now covers two matrices: 9-cell single-module §2 (`SmokeTest`) and 6-cell §2a project-shape (`ProjectShapeSmokeTest`) covering multi-module / `include("build-logic")` / `includeBuild` composite × Kotlin/Groovy DSL — together replacing every manual cell from `SMOKE_TEST.md` except the §3 mavenLocal coordinates round-trip. The §2a driver also sweeps every Java source for `@Slf4j` after rewriteRun to guard against silent no-op failures where `compileJava` would still pass on the unmodified source. `publishAndReleaseToMavenCentral` (and the related Maven Central publish tasks) now `dependsOn("smokeTest")` so the pre-publish gate is structural, not operator discipline. CI workflow updated to install JDK 25 alongside JDK 21 (the clean-code plugin needs the former, the integration + smoke tasks need the latter as a launcher). Bumped the integrationTest forked JVM heap to 2g and the Gradle daemon to 2g — 512 MB default OOMs once multiple integration tests run back-to-back in one JVM (intermittent `ClassCastException: ParseError → K.CompilationUnit` from the build.gradle.kts parse aborting under memory pressure). And: re-bisected the JDK 25 + Gradle 9 catalog issue — the original diagnosis conflated the two, the actual trigger is Gradle 9.x daemon (independent of JDK launcher), upstream issue draft saved to `UPSTREAM_ISSUE_DRAFT.md`.

## Active

- **Expand integration coverage further** — currently covered: `AddLombokDependency` (inline + catalog), `AddSlf4jDependencies` (inline dedup-ordering regression guard + catalog seeding), `MigrateToSlf4jRecipe` (mixed-pattern end-to-end including XML seeding), `CreateLog4j2Config` (fresh project + `overwriteExisting=false` idempotency), `JavaTransformsClasspathGated` negative case. Still bootstrap-only: `SystemOutToSlf4jRecipe` (likely covered transitively by `MigrateToSlf4jRecipe`); multi-module / `includeBuild` shapes (the harness only models a single `forProjectDirectory`).

- **Smoke-test automation Phase 4: §3 mavenLocal coordinates round-trip** — only manual smoke step remaining. Phase 1 (single-module §2), Phase 2 (multi-module / build-logic / composite §2a), and Phase 3 (publish-task gating) all shipped. ~1 hr if a release-shaped consumer project is bootstrapped from a template; less if it reuses `ProjectShapeScaffolder`.

- **Re-enable Java 25 in integration tests** — re-bisected 2026-05-02 (originally conflated JDK 25 and Gradle 9; the third cell isolated the trigger). Catalog regression is a **Gradle 9.x daemon** behaviour change in `org.openrewrite.gradle.AddDependency`, **independent of JDK launcher version**. `openrewrite-core 8.81.3` throughout, JDK 21 launcher throughout:

  | Gradle daemon | inline | catalog |
  |---|---|---|
  | 8.14.3 | pass | pass |
  | 9.0.0  | pass | **no edit to `build.gradle.kts`** |
  | 9.4.1  | pass | **no edit to `build.gradle.kts`** |

  Reproduces with `org.openrewrite.gradle.AddDependency` invoked directly (no project recipes wrapping it) — the bug is upstream, not in our composition. Inline dep declarations work fine on Gradle 9; only the catalog-aware code path in `AddDependency` fails.

  Older `openrewrite-core` versions can't run on Gradle 9 at all: 8.81.1 and earlier reject the `-b` flag — the `isGradle9OrLater` gate landed in 8.81.2, so 8.81.2 is the *first* `openrewrite-core` that runs on Gradle 9. `8.82.0-SNAPSHOT` can't be tested because only `org.openrewrite.gradle.tooling:model` is published to OSSRH snapshots, not the full `rewrite-*` set.

  **Not the same bug as #6132.** That one (JDK 25 launcher → Kotlin 1.9.x can't parse build.gradle.kts) was fixed by the K2 upgrade in #6766 (`kotlin-compiler-embeddable 2.2.0`). With K2 in place, inline scenarios work on JDK 25 + Gradle 9, but the catalog scenario still fails — confirming it's a separate bug.

  Path forward: upstream issue draft saved to `UPSTREAM_ISSUE_DRAFT.md` (file at `https://github.com/openrewrite/rewrite/issues/new` — local `gh` token lacks write access to that repo). After filing, paste the issue URL back here. Production users on Gradle 9 are covered by the manual `SMOKE_TEST.md`. Current state: integrationTest stays on JDK 21 launcher + Gradle 8.x daemon until upstream fixes Gradle 9 catalog handling.

## Parked (re-open on request)

- **GitHub Packages publish** — the attempt during 0.1 got 403 because the ambient `GITHUB_TOKEN` lacks `write:packages`. Maven Central is the real channel; this would only be a mirror. Not worth the auth setup for the marginal benefit. Re-open if a consumer specifically asks for the GitHub Packages coordinates.

- **Auto version-detection at recipe runtime** — for now we pin Lombok / SLF4J / Log4j2 versions explicitly and check Maven Central by hand pre-release (`SMOKE_TEST.md`). Full runtime-detection would be a custom recipe that queries Central when it runs.

- **Log-and-throw cleanup** — detect `log.error(msg, e); throw …;` and drop the log so the exception isn't double-reported. Nontrivial because it needs to reason about whether the throw reaches a boundary where something else logs it. Explicitly deferred.
