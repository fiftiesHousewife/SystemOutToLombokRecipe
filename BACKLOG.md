# Backlog

## Shipped

- **0.3** — version-catalog-aware `SystemOutToLombokLog4jRecipeCatalog` composition.
- **0.4** — catalog auto-wired into `build.gradle.kts`; JUL conversion; manual-Log4j2-logger migration; production-ready `log4j2.xml` + console-only `log4j2-test.xml`; pre-release `SMOKE_TEST.md`.
- **0.5** — pivot to `@Slf4j` + SLF4J API + Log4j2 backend (via `log4j-slf4j2-impl`); unified catalog auto-detect in a single `SystemOutToSlf4jRecipe`; JUL field and import cleaned up after call conversion; deps pinned to explicit versions; recipe family renamed accordingly (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `AddSlf4jDependencies`, `AddLombokDependency`).
- **0.6** — per-module Lombok classpath gating (`requireLombokOnClasspath` option on the annotation-adding recipes, wired through the `*NoDeps` variants via a new `JavaTransformsClasspathGated` composition); Groovy DSL support for `UseCatalogReferenceForDependency` (paren-less idiom preserved via `JContainer.withElements` + manual `J.FieldAccess` + `OmitParentheses` marker copy); 14-cell project-shape matrix (`KotlinDslMatrixTest` + `GroovyDslMatrixTest`) covering single-module × multi-module × `build-logic` × `buildSrc` × catalog × inline × `gradle.properties`-interpolated; four new multi-module / build-logic smoke-test templates in `SMOKE_TEST.md` §2a; new `MigrateToSlf4jRecipe` (+ `NoDeps`) composing `ConvertManualLoggerToSlf4j` ahead of the standard `JavaTransforms` chain so mixed-pattern codebases need only one rewriteRun; `AddDependency` same-group dedup-ordering quirk documented inline in the YAML; `build.gradle.kts` post-rewrite formatting quirk documented in README troubleshooting.

## Active

- **GitHub Packages publish** — the attempt during 0.1 got 403 because the ambient `GITHUB_TOKEN` lacks `write:packages`. Maven Central is doing the heavy lifting so this is a belt-and-braces extra, but still nominally open if we want the mirror.

- **RewriteTest multi-source integration harness** — the OpenRewrite-idiomatic way to verify composed recipes (`java()` + `buildGradleKts()` + `toml()` in one `rewriteRun`) needs `withToolingApi()`, which pulls in Gradle's full tooling runtime from a non-Maven-Central repo. Tried and backed out. Worth revisiting if the project grows — meanwhile the `/tmp` smoke test in `SMOKE_TEST.md` is the verification gate.

## Parked (re-open on request)

- **Auto version-detection at recipe runtime** — for now we pin Lombok / SLF4J / Log4j2 versions explicitly and check Maven Central by hand pre-release (`SMOKE_TEST.md`). Full runtime-detection would be a custom recipe that queries Central when it runs.

- **Log-and-throw cleanup** — detect `log.error(msg, e); throw …;` and drop the log so the exception isn't double-reported. Nontrivial because it needs to reason about whether the throw reaches a boundary where something else logs it. Explicitly deferred.
