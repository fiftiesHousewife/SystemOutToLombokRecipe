# Backlog

## Shipped

- **0.3** — version-catalog-aware `SystemOutToLombokLog4jRecipeCatalog` composition.
- **0.4** — catalog auto-wired into `build.gradle.kts`; JUL conversion; manual-Log4j2-logger migration; production-ready `log4j2.xml` + console-only `log4j2-test.xml`; pre-release `SMOKE_TEST.md`.
- **0.5** — pivot to `@Slf4j` + SLF4J API + Log4j2 backend (via `log4j-slf4j2-impl`); unified catalog auto-detect in a single `SystemOutToSlf4jRecipe`; JUL field and import cleaned up after call conversion; deps pinned to explicit versions; recipe family renamed accordingly (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `AddSlf4jDependencies`, `AddLombokDependency`).

## Active

- **Roll JUL fixing and vanilla Log4j2 migration into the top-level recipe** — today `SystemOutToSlf4jRecipe` handles `System.out` / `printStackTrace` / JUL, while `ConvertManualLoggerToSlf4jRecipe` handles hand-rolled Log4j2 `Logger log = LogManager.getLogger(…)` fields. A codebase that has a mix of all four patterns currently needs two separate `rewriteRun` invocations. Compose them into a single top-level recipe so callers run one thing and everything gets converted. Keep the focused sub-recipes available for people who want them individually.

- **Per-module Lombok classpath gating (from 0.5 user feedback)** — the `*RecipeNoDeps` variants add `@Slf4j` to every matching class unconditionally, even in modules that don't actually have Lombok on their classpath. Consumer project reports "0.5 does NOT close the per-module Lombok classpath-gating gap (the NoDeps variant still adds `@Slf4j` unconditionally); version pinned at 0.5, recipe remains commented out." Fix: before `AddLombokSlf4jAnnotation` writes the annotation, verify Lombok (`lombok.extern.slf4j.Slf4j`) is resolvable on the current source's classpath — probably via `JavaSourceSet` / `JavaProject` markers OpenRewrite attaches at parse time, or a `UsesType`-style pre-check. Highest priority on this list — currently blocking at least one real user.

- **Groovy DSL (`build.gradle`) coverage** — only `build.gradle.kts` is smoke-tested. OpenRewrite's `AddDependency` handles both, but we haven't exercised the Groovy path. Fix is probably "run the existing smoke test on a Groovy-DSL sample and patch whatever breaks".

- **GitHub Packages publish** — the attempt during 0.1 got 403 because the ambient `GITHUB_TOKEN` lacks `write:packages`. Maven Central is doing the heavy lifting so this is a belt-and-braces extra, but still nominally open if we want the mirror.

- **RewriteTest multi-source integration harness** — the OpenRewrite-idiomatic way to verify composed recipes (`java()` + `buildGradleKts()` + `toml()` in one `rewriteRun`) needs `withToolingApi()`, which pulls in Gradle's full tooling runtime from a non-Maven-Central repo. Tried and backed out. Worth revisiting if the project grows — meanwhile the `/tmp` smoke test in `SMOKE_TEST.md` is the verification gate.

- **`AddDependency` dedup / ordering quirk** — when two `runtimeOnly` `AddDependency` calls target the same group (`org.apache.logging.log4j`), the second is dropped unless ordered before the first. We work around it by ordering `log4j-core` before `log4j-slf4j2-impl` in the YAML. Worth filing upstream at some point; a short comment in the YAML would help the next reader.

- **`build.gradle.kts` formatting after transform** — when the original `dependencies { ... }` block is a single line, OpenRewrite inserts new lines with mixed indentation and a dangling `}`. Not fixable from our side; cosmetic; consumers can run a formatter. Worth a line in the README troubleshooting section.

## Parked (re-open on request)

- **Auto version-detection at recipe runtime** — for now we pin Lombok / SLF4J / Log4j2 versions explicitly and check Maven Central by hand pre-release (`SMOKE_TEST.md`). Full runtime-detection would be a custom recipe that queries Central when it runs.

- **Log-and-throw cleanup** — detect `log.error(msg, e); throw …;` and drop the log so the exception isn't double-reported. Nontrivial because it needs to reason about whether the throw reaches a boundary where something else logs it. Explicitly deferred.
