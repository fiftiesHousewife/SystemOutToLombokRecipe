# Backlog

## Shipped

- **0.3** — version-catalog-aware `SystemOutToLombokLog4jRecipeCatalog` composition.
- **0.4** — catalog auto-wired into `build.gradle.kts`; JUL conversion; manual-Log4j2-logger migration; production-ready `log4j2.xml` + console-only `log4j2-test.xml`; pre-release `SMOKE_TEST.md`.
- **0.5** — pivot to `@Slf4j` + SLF4J API + Log4j2 backend (via `log4j-slf4j2-impl`); unified catalog auto-detect in a single `SystemOutToSlf4jRecipe`; JUL field and import cleaned up after call conversion; deps pinned to explicit versions; recipe family renamed accordingly (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `AddSlf4jDependencies`, `AddLombokDependency`).
- **0.6** — per-module Lombok classpath gating (`requireLombokOnClasspath` option on the annotation-adding recipes, wired through the `*NoDeps` variants via a new `JavaTransformsClasspathGated` composition); Groovy DSL support for `UseCatalogReferenceForDependency` (paren-less idiom preserved via `JContainer.withElements` + manual `J.FieldAccess` + `OmitParentheses` marker copy); 14-cell project-shape matrix (`KotlinDslMatrixTest` + `GroovyDslMatrixTest`) covering single-module × multi-module × `build-logic` × `buildSrc` × catalog × inline × `gradle.properties`-interpolated; four new multi-module / build-logic smoke-test templates in `SMOKE_TEST.md` §2a; new `MigrateToSlf4jRecipe` (+ `NoDeps`) composing `ConvertManualLoggerToSlf4j` ahead of the standard `JavaTransforms` chain so mixed-pattern codebases need only one rewriteRun; `AddDependency` same-group dedup-ordering quirk documented inline in the YAML; `build.gradle.kts` post-rewrite formatting quirk documented in README troubleshooting.

- **`withToolingApi()` integration harness** (post-0.6) — `org.openrewrite.gradle.tooling:model` is now on Maven Central, so the harness no longer needs a non-Central repo. Wired up as a separate `integrationTest` source set + Gradle task that compiles at `release = 21` and runs on a JDK 21 launcher, because the embedded Gradle daemon (8.14.3 by default) bundles a Groovy/ASM that can't read Java 25 bytecode and the model still uses the `-b` flag that Gradle 9 removed. First test (`AddLombokDependencyIntegrationTest`) covers `AddLombokDependency` end-to-end with real `GradleProject` resolution against Maven Central, in both inline and catalog shapes; immediately surfaced an `AddDependency` ordering quirk (annotationProcessor printed before compileOnly with a blank line between) that the matrix tests' hand-rolled `GradleProject` marker can't catch. `./gradlew check` now depends on `integrationTest`.

## Active

- **GitHub Packages publish** — the attempt during 0.1 got 403 because the ambient `GITHUB_TOKEN` lacks `write:packages`. Maven Central is doing the heavy lifting so this is a belt-and-braces extra, but still nominally open if we want the mirror.

- **Expand integration coverage** — first integration test only covers `AddLombokDependency`. Worth adding cases for `MigrateToSlf4jRecipe` (the full mixed-pattern composition) and `SystemOutToSlf4jRecipe` to lift more of the verification weight off `SMOKE_TEST.md`. Multi-module / `includeBuild` shapes likely still need the `/tmp` bootstrap (the harness only models a single `forProjectDirectory`).

- **Re-enable Java 25 in integration tests** — once OpenRewrite ships a model that supports Gradle 9 (drops the `-b` flag) and the bundled Groovy/ASM can read Java 25 bytecode, drop the dedicated `release = 21` source set and let `integrationTest` use the JDK 25 toolchain like the rest of the build.

## Parked (re-open on request)

- **Auto version-detection at recipe runtime** — for now we pin Lombok / SLF4J / Log4j2 versions explicitly and check Maven Central by hand pre-release (`SMOKE_TEST.md`). Full runtime-detection would be a custom recipe that queries Central when it runs.

- **Log-and-throw cleanup** — detect `log.error(msg, e); throw …;` and drop the log so the exception isn't double-reported. Nontrivial because it needs to reason about whether the throw reaches a boundary where something else logs it. Explicitly deferred.
