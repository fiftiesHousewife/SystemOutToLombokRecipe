# Clean Logging

Automated SLF4J / Lombok hygiene for Java codebases. A suite of OpenRewrite recipes that take any legacy logging shape — `System.out`, `printStackTrace`, JUL, Apache Commons Logging, hand-rolled Log4j2 or SLF4J `Logger` fields, sloppy SLF4J usage — and converge it onto Lombok `@Slf4j` + parameterised SLF4J calls (with Log4j2 as the backend). Covers the full spectrum of "how Java code accidentally ended up emitting log lines":

- **`System.out` / `System.err` / `printStackTrace()`** — the "poor man's logging" anti-patterns that drag console output into business code.
- **`java.util.logging` (JUL)**, **Apache Commons Logging**, and **hand-rolled Log4j2** logger fields — pre-SLF4J framework migrations onto a single Lombok-generated `log` field.
- **Hand-rolled SLF4J fields** — for codebases already on SLF4J that still spell out `private static final Logger log = LoggerFactory.getLogger(X.class);`.
- **SLF4J usage cleanups** — concatenated message strings parameterised into `{}` placeholders; trailing-`{}` placeholders that silently consume a `Throwable` (and lose the stack trace) restored to the proper trailing-throwable slot; concatenated `getMessage()` chains peeled into a separate throwable arg.

Your application code talks to SLF4J (the standard Java logging facade), so it's never coupled to a specific backend. Log4j2 handles the actual log routing and file rolling under the covers via the `log4j-slf4j2-impl` bridge.

## Why this project? (and how does it compare to `rewrite-logging-frameworks`?)

OpenRewrite's recipe ecosystem ships [`rewrite-logging-frameworks`](https://docs.openrewrite.org/recipes/java/logging) — an excellent, mature module that converts between SLF4J / Log4j1 / Log4j2 / JUL / Commons Logging. It's a **peer** of this project, not an upstream: clean-logging doesn't depend on it at runtime, and the two pursue different opinions about what "migrated" means. If `rewrite-logging-frameworks` matches your taste, use it directly — it's broader and better-tested across logging-framework permutations.

Where the two projects differ in opinion:

| Aspect | `rewrite-logging-frameworks` | Clean Logging |
| --- | --- | --- |
| **Logger field shape** | Hand-rolled `private static final Logger log = LoggerFactory.getLogger(...)` via the `AddLogger` machinery. | **Lombok `@Slf4j`.** No field appears in the class body — the annotation generates it. |
| **Gradle version-catalog awareness** | None — `AddDependency` writes inline `"group:artifact:version"` strings into `build.gradle.kts`. | **Auto-detect.** When `gradle/libs.versions.toml` is present, entries land in the catalog and the build file uses `libs.lombok` / `libs.slf4jApi` / `libs.log4jSlf4jImpl` / `libs.log4jCore`. Falls back to inline strings when no catalog exists. |
| **Multi-module classpath gating** | None. Recipes apply uniformly across every source set. Modules without Lombok end up rewritten into uncompilable code. | **`requireLombokOnClasspath` option** (and `*NoDeps` recipe variants) skip `@Slf4j` insertion in modules whose classpath doesn't resolve `lombok.extern.slf4j.Slf4j`. |
| **One-shot front door** | No single composed recipe; users compose framework-specific recipes themselves. | `MigrateToCleanLoggingRecipe` folds the full pipeline (Commons + Log4j2 + SLF4J + JUL + `System.out` + SLF4J cleanups) into one `rewriteRun`. |
| **`System.out` / `System.err` / `printStackTrace`** | Has `PrintStackTraceToLogError` for `printStackTrace`; no `System.out` recipe. | Both. `SystemOutToSlf4j` covers `println`/`print`/`printf` (printf specifiers convert to `{}` placeholders); `PrintStackTraceToLog` covers `printStackTrace()` plus the `printStackTrace(System.err)` / `printStackTrace(System.out)` overloads. |
| **Concat → parameterised SLF4J** | `ParameterizedLogging` (works on calls whose receiver type resolves to an SLF4J `Logger`). | `Slf4jConcatToParameterized` (structural detection on receiver name `log`, the `@Slf4j` convention). Works on calls inserted earlier in the same pipeline whose LST type info is stale — `rewrite-logging-frameworks`'s `UsesMethod` precondition silently skips those. |
| **Trailing-`{}` swallowing a `Throwable`** | Not addressed. | `ThrowableLastArgumentNoPlaceholder` detects `log.error("failed: {}", e)` (where the throwable falls into the placeholder via `toString()` and the stack trace is silently lost) and rewrites to `log.error("failed", e)`. |
| **Concat-getMessage** | Not addressed. | `ConcatThrowableMessage` rewrites `log.error("failed: " + e.getMessage())` to `log.error("failed: ", e)` so the stack trace gets logged. |
| **JUL `isLoggable` / lambda suppliers** | Has its own `JulToSlf4j` covering basic level methods. | `JulToSlf4j` adds `logger.isLoggable(Level.FINE)` → `log.isDebugEnabled()` rewriting and lambda-supplier overload unwrapping (`logger.fine(() -> "v=" + v)` → `log.debug("v=" + v)`). Block-body lambdas and bare `Supplier` references are left alone. |
| **Apache Commons `fatal`** | Maps `fatal` to `error` as part of its Commons → SLF4J recipe. | Same — `CommonsLoggingToSlf4j` rewrites `fatal` / `isFatalEnabled` → `error` / `isErrorEnabled`. |
| **Adoption** | Wide. The `org.openrewrite.recipe:rewrite-logging-frameworks` artifact is part of the standard recipe BOM and pulled in by many migration paths. | Narrow. One project (this one), one artifact (`io.github.fiftieshousewife:clean-logging`), pinned dependencies, opinionated about Lombok. |

The short version: **if you want `@Slf4j` everywhere, catalog-aware deps, and multi-module gating, use this. If you want directly-declared `Logger` fields and don't care about Gradle catalogs or Lombok, use `rewrite-logging-frameworks`.** A `rewrite-logging-frameworks` migration followed by a focused clean-logging recipe (e.g. `DirectSlf4jLoggerFieldToLombokRecipe`) to lift the resulting `Logger` field onto `@Slf4j` is also a valid composition — see [Migrating existing SLF4J code](#migrating-existing-slf4j-code).

## What It Does

The default recipe, `MigrateToCleanLoggingRecipe`, takes any Java project — whatever mix of legacy logging it has — and converges it onto Lombok `@Slf4j` + parameterised SLF4J calls in a single pass. On any given class it will:

1. **Add the right dependencies to your build.**
   Lombok (`compileOnly` + `annotationProcessor`), SLF4J (`slf4j-api`), the SLF4J-to-Log4j2 bridge (`log4j-slf4j2-impl`), and the Log4j2 backend (`log4j-core`). The recipe auto-detects whether the project uses a Gradle version catalog: if `gradle/libs.versions.toml` exists, entries land in the catalog and your `build.gradle.kts` gets `libs.lombok`/`libs.slf4jApi`/`libs.log4jSlf4jImpl`/`libs.log4jCore`; otherwise inline `"group:artifact:version"` declarations are added.

2. **Create the Log4j2 configs.**
   `src/main/resources/log4j2.xml` — production-ready: non-error levels to stdout, errors to stderr (respecting the `System.out` vs `System.err` split), plus a rolling file appender under `./logs/` (daily + 10 MB gzip rollover, keeps 10 files).
   `src/test/resources/log4j2-test.xml` — console only, so unit tests don't spam `./logs/`.

3. **Replace every legacy logger field** — Apache Commons Logging `Log`, hand-rolled Log4j2 `Logger`, hand-rolled SLF4J `Logger` — with `@Slf4j`, dropping the field and renaming references (`logger`/`LOG`/`LOGGER` → `log`), and cleaning up the now-unused framework imports.

4. **Add `@Slf4j`** to every class that emits log lines but doesn't yet have a logger — whether via `System.out`/`System.err`, `printStackTrace()`, or a `java.util.logging.Logger` field.

5. **Rewrite the call sites:**

   **`System.out` / `System.err`** — stops the "poor man's logging" anti-pattern of writing straight to the console:
   - `System.out.println(...)` → `log.info(...)`
   - `System.err.println(...)` → `log.error(...)`
   - `System.out.printf("Name: %s, Age: %d%n", ...)` → `log.info("Name: {}, Age: {}", ...)` — printf specifiers become parameterized `{}` placeholders so the log framework can do structured formatting (and skip it when the level is disabled).
   - `System.out.println("x = " + x)` → `log.info("x = {}", x)` — string-concatenated messages are decomposed into a format string plus args, so the logger isn't paying for string-building on disabled levels.

   **`Throwable.printStackTrace()`** — the other popular "logging by accident" pattern. Replaced with `log.error("Exception occurred", exception)` so the trace goes through the configured appenders, with the exception properly attached rather than dumped to stderr. The `printStackTrace(System.err)` and `printStackTrace(System.out)` overloads are handled the same way (the stream argument is dropped — the rewritten `log.error` call routes to stderr via the level alone).

   **`java.util.logging.Logger` (JUL)** — the JDK's built-in logging framework is awkward (static `Level` values, no parameterized messages, minimal out-of-the-box config). Migrating to Log4j2 gives you the same features as the rest of the code base. The recipe maps JUL levels to the closest Log4j2 equivalent:
   - `severe` → `error`
   - `warning` → `warn`
   - `info` → `info`
   - `config` / `fine` → `debug`
   - `finer` / `finest` → `trace`

   After the call conversion, the hand-rolled `Logger logger = Logger.getLogger(...)` field and the `java.util.logging.Logger` import are removed if nothing else in the class still references them.

   **Apache Commons Logging** — `fatal` / `isFatalEnabled` are rewritten to `error` / `isErrorEnabled` (SLF4J has no fatal level). Other Commons Logging level methods (`error` / `warn` / `info` / `debug` / `trace` and their `is*Enabled`) are name-compatible with SLF4J and pass through.

6. **Tidy up the SLF4J calls** that result, plus any pre-existing ones:
   - Concatenated messages → parameterised `{}` placeholders so the message string is only assembled when the level is enabled.
   - Trailing `{}` placeholders that silently consume a `Throwable` (and lose the stack trace) are dropped so the throwable lands on the trailing-throwable slot.

If you only have one source pattern, focused recipes (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`, `DirectSlf4jLoggerFieldToLombokRecipe`, `CommonsLoggingToSlf4jRecipe`) are still published and run faster — see [Recipes → At a glance](#at-a-glance) below.

## Prerequisites

- JDK 17 or later (JDK 25 supported)
- Gradle 8.x or 9.x

> [!IMPORTANT]
> If your project uses a Gradle version catalog (`gradle/libs.versions.toml`),
> we recommend Gradle 8.14.x until [openrewrite/rewrite#7548][] ships. On Gradle
> 9.x, the upstream `AddDependency` recipe no-ops against `build.gradle.kts`
> when a catalog is present — the catalog gets the new `[versions]` and
> `[libraries]` entries, but the `dependencies { ... }` block in your build
> script doesn't get the corresponding `compileOnly(libs.lombok)` line. Inline
> dependency declarations are unaffected.

[openrewrite/rewrite#7548]: https://github.com/openrewrite/rewrite/issues/7548

Supports transforming source code written in Java 8 through Java 25.

## Supported project shapes

Exercised by the matrix tests under `src/test/java/io/github/fiftieshousewife/cleanlogging/matrix/` and pre-release smoke-tested against real Gradle projects (`SMOKE_TEST.md` §2a).

| Dimension | Supported | Notes |
| --- | --- | --- |
| Kotlin DSL (`build.gradle.kts`) | ✓ | Catalog references emit `compileOnly(libs.lombok)`. |
| Groovy DSL (`build.gradle`) | ✓ | Catalog references preserve the idiomatic paren-less form: `compileOnly libs.lombok`. |
| Version catalog (`gradle/libs.versions.toml`) | ✓ | Auto-detected; when present, inline deps rewrite to `libs.xxx`. |
| Inline `"group:artifact:version"` deps | ✓ | Left as-is when there's no catalog. |
| `gradle.properties`-interpolated versions (`${someVersion}`) | ✓ don't-regress | GString / Kotlin template interpolations are left alone (not rewritten, not double-added). |
| Single-module | ✓ | |
| Multi-module | ✓ | Each subproject is processed independently. |
| `build-logic` / composite convention plugins (with their own nested `gradle/libs.versions.toml`) | ✓ | The convention plugin's build file is rewritten using its own catalog. |
| `buildSrc` convention plugins | ✓ | Same as a regular subproject; uses the root catalog. |

If you hit a shape that's not listed, please open an issue with a minimal reproducer.

## Quick Start

```bash
./gradlew build
```

## Using in Your Project

1. **Add versions to `gradle/libs.versions.toml`**:
```toml
[versions]
openrewrite = "7.30.0"
clean-logging = "1.0"

[libraries]
clean-logging = { module = "io.github.fiftieshousewife:clean-logging", version.ref = "clean-logging" }

[plugins]
openrewrite = { id = "org.openrewrite.rewrite", version.ref = "openrewrite" }
```

2. **Add to your `build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.openrewrite)
}

dependencies {
    rewrite(libs.clean.logging)
}

rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.MigrateToCleanLoggingRecipe")
}
```

3. **Run**:
```bash
./gradlew rewriteDryRun  # Preview
./gradlew rewriteRun     # Apply
```

4. **Tidy up (optional)**. Run your IDE's formatter or `ktfmt`/`kotlinter` on `build.gradle.kts` — the underlying Gradle `AddDependency` recipe can leave mixed whitespace in the `dependencies { ... }` block when dependencies are inserted into a single-line block. The result compiles, it's only cosmetic.

### Versions installed

Lombok `1.18.44`, SLF4J `2.0.17`, and Log4j2 `2.25.4` at the time of this release. These are pinned so the transform is deterministic. To pick up later patch releases, add the Ben-Manes `versions` plugin to your project (`./gradlew dependencyUpdates`) and bump the catalog entries or inline strings by hand after the recipe runs.

## Recipes

### At a glance

**Default**: `MigrateToCleanLoggingRecipe`. Targeted recipes are listed below for codebases with only one source pattern.

| Recipe | What it migrates | Adds deps? | Use when |
| --- | --- | --- | --- |
| **`MigrateToCleanLoggingRecipe`** | **Everything**: Commons Logging + manual Log4j2 / SLF4J fields + JUL + `System.out` / `System.err` / `printStackTrace` + SLF4J cleanups, in one pass | yes (catalog-aware) | **Default.** Use this unless you specifically want a narrower scope. |
| **`MigrateToCleanLoggingRecipeNoDeps`** | Same, no dependency edits | no | Multi-module projects where Lombok / SLF4J / Log4j2 deps live on a parent. |
| **`SystemOutToSlf4jRecipe`** | `System.out`/`System.err`/`printStackTrace` + JUL → `@Slf4j` | yes (catalog-aware) | Targeted: codebase only has console output and/or JUL. |
| **`SystemOutToSlf4jRecipeNoDeps`** | Same transforms, no dependency edits | no | Multi-module variant. |
| **`ConvertManualLoggerToSlf4jRecipe`** | Hand-rolled Log4j2 `Logger` field → `@Slf4j` | yes (catalog-aware) | Targeted: codebase already on Log4j2 with manual `LogManager.getLogger(...)` fields. |
| **`ConvertManualLoggerToSlf4jRecipeNoDeps`** | Same, no dependency edits | no | Multi-module variant. |
| **`DirectSlf4jLoggerFieldToLombokRecipe`** | Hand-rolled SLF4J `Logger` field → `@Slf4j` | yes (Lombok only) | Targeted: codebase already on SLF4J with manual `LoggerFactory.getLogger(...)` fields. |
| **`DirectSlf4jLoggerFieldToLombokRecipeNoDeps`** | Same, no dependency edits | no | Multi-module variant. |
| **`CommonsLoggingToSlf4jRecipe`** | Apache Commons Logging `Log` field → `@Slf4j`, plus `fatal`→`error` | yes (Lombok only) | Targeted: legacy codebases on `org.apache.commons.logging.Log` / `LogFactory`. |
| **`CommonsLoggingToSlf4jRecipeNoDeps`** | Same, no dependency edits | no | Multi-module variant. |
| **`MigrateToSlf4jRecipe`** | All `*ToSlf4j` paths *except* Commons Logging, in one pass + SLF4J cleanups | yes (catalog-aware) | Mixed codebases on JUL / Log4j2 / `System.out` (no Commons). |
| **`MigrateToSlf4jRecipeNoDeps`** | Same, no dependency edits | no | Multi-module variant. |

The leaf-level `*` recipes (no `Recipe` suffix — `Slf4jConcatToParameterized`, `ThrowableLastArgumentNoPlaceholder`, `ConcatThrowableMessage`) are SLF4J cleanups that never touch dependencies. See [SLF4J cleanup recipes](#slf4j-cleanup-recipes).

### `MigrateToCleanLoggingRecipe` — the one you usually want

The full Clean Logging pipeline. Runs every leaf transform in a single pass, **auto-detects** your dependency setup, and seeds the Log4j2 configs:

- Apache Commons Logging `Log` fields → `@Slf4j` (with `fatal` → `error`).
- Hand-rolled Log4j2 `Logger log = LogManager.getLogger(...)` fields → `@Slf4j`.
- Hand-rolled SLF4J `Logger log = LoggerFactory.getLogger(...)` fields → `@Slf4j`.
- `java.util.logging.Logger` calls → `log.xxx`, with the JUL field + import removed.
- `System.out` / `System.err` / `printStackTrace()` → `log.info` / `log.error`.
- SLF4J cleanups: concatenated messages → `{}` placeholders; trailing-`{}` placeholders that consumed a `Throwable` arg → throwable in the trailing slot.
- Adds Lombok + SLF4J + Log4j2 backend dependencies, catalog-aware: if `gradle/libs.versions.toml` is present, entries land in the catalog and `build.gradle.kts` uses `libs.lombok` / `libs.slf4jApi` / `libs.log4jSlf4jImpl` / `libs.log4jCore`; otherwise inline `compileOnly("...")` declarations.
- Seeds production-ready `log4j2.xml` + console-only `log4j2-test.xml`.

You don't pick a variant — the recipe figures it out.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.MigrateToCleanLoggingRecipe")
}
```

A `NoDeps` variant (`MigrateToCleanLoggingRecipeNoDeps`) exists for multi-module projects where Lombok / SLF4J / Log4j2 deps live on a parent. With `NoDeps`, only modules whose classpath actually contains `lombok.extern.slf4j.Slf4j` are rewritten — modules without Lombok are skipped rather than rewritten into uncompilable code.

### Targeted variants (one source pattern at a time)

If you only have one source pattern to fix and want a faster, narrower run, the focused recipes are still published:

- `SystemOutToSlf4jRecipe` — `System.out` / `System.err` / `printStackTrace` + JUL only.
- `ConvertManualLoggerToSlf4jRecipe` — hand-rolled Log4j2 `Logger` fields only. See [Migrating existing Log4j2 code](#migrating-existing-log4j2-code).
- `DirectSlf4jLoggerFieldToLombokRecipe` — hand-rolled SLF4J `Logger` fields only. See [Migrating existing SLF4J code](#migrating-existing-slf4j-code).
- `CommonsLoggingToSlf4jRecipe` — Apache Commons Logging only. See [Migrating Apache Commons Logging code](#migrating-apache-commons-logging-code).
- `MigrateToSlf4jRecipe` — JUL + Log4j2 + `System.out` (no Commons Logging).

Each has a matching `*NoDeps` variant for the multi-module case.

## Migrating existing Log4j2 code

If your codebase already uses Log4j2 but declares `Logger` fields by hand (`private static final Logger log = LogManager.getLogger(X.class);`), `ConvertManualLoggerToSlf4jRecipe` removes that boilerplate:

- Adds `@Slf4j` to each affected class.
- Deletes the manual field.
- Renames any references to the old field (`logger.info(...)`, `LOG.error(...)`) to `log.xxx(...)`.
- Drops now-unused `org.apache.logging.log4j.Logger` / `LogManager` imports.
- Adds the deps the rewritten code needs — Lombok (compileOnly + annotationProcessor), `slf4j-api` (for the `org.slf4j.Logger` field that `@Slf4j` generates), and the `log4j-slf4j2-impl` bridge so SLF4J calls still route through Log4j2 at runtime. Catalog-aware (same auto-detect as above).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.ConvertManualLoggerToSlf4jRecipe")
}
```

A `NoDeps` variant (`ConvertManualLoggerToSlf4jRecipeNoDeps`) exists for projects that manage Lombok separately. If you have both this *and* `System.out` / JUL patterns to migrate, see `MigrateToSlf4jRecipe` above.

## Migrating existing SLF4J code

If your codebase is already on SLF4J but declares `Logger` fields by hand (`private static final Logger log = LoggerFactory.getLogger(X.class);`), `DirectSlf4jLoggerFieldToLombokRecipe` removes that boilerplate:

- Adds `@Slf4j` to each affected class.
- Deletes the manual field.
- Renames any references to the old field (`logger.info(...)`, `LOG.error(...)`) to `log.xxx(...)`.
- Drops now-unused `org.slf4j.Logger` / `org.slf4j.LoggerFactory` imports.
- Adds the Lombok `compileOnly` + `annotationProcessor` deps so `@Slf4j` expansion compiles. Catalog-aware (same auto-detect as above).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.DirectSlf4jLoggerFieldToLombokRecipe")
}
```

A `NoDeps` variant (`DirectSlf4jLoggerFieldToLombokRecipeNoDeps`) exists for projects that manage Lombok separately — typical for multi-module projects where Lombok lives at a parent level. The `NoDeps` variant only converts source files whose classpath actually contains `lombok.extern.slf4j.Slf4j`, so modules without Lombok are skipped rather than rewritten into uncompilable code.

The recipe only touches classes with **exactly one** eligible field — `private` or package-private, `static final Logger`, initialised from `LoggerFactory.getLogger(...)`. Public/protected fields, classes that already carry a Lombok logging annotation, classes with no field, and classes with multiple eligible fields are all skipped.

## Migrating Apache Commons Logging code

If your codebase uses Apache Commons Logging (`private static final Log log = LogFactory.getLog(X.class);`), `CommonsLoggingToSlf4jRecipe` lifts it onto `@Slf4j`:

- Adds `@Slf4j` to each affected class.
- Deletes the manual `Log` field.
- Renames any references to the old field to `log.xxx(...)`.
- Drops now-unused `org.apache.commons.logging.Log` / `LogFactory` imports.
- Rewrites `fatal`/`isFatalEnabled` to `error`/`isErrorEnabled` since SLF4J has no fatal level. Other Commons Logging level methods (`error`/`warn`/`info`/`debug`/`trace` and their `is*Enabled`) are name-compatible with SLF4J and pass through untouched.
- Adds the Lombok `compileOnly` + `annotationProcessor` deps so `@Slf4j` expansion compiles. Catalog-aware (same auto-detect as above).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.CommonsLoggingToSlf4jRecipe")
}
```

| Commons Logging | SLF4J | Notes |
| --- | --- | --- |
| `log.fatal(msg)` | `log.error(msg)` | SLF4J has no fatal level. |
| `log.fatal(msg, t)` | `log.error(msg, t)` | Throwable arg preserved. |
| `log.isFatalEnabled()` | `log.isErrorEnabled()` | Same mapping. |
| `log.error/warn/info/debug/trace(...)` | `log.error/warn/info/debug/trace(...)` | Names already match — no rewrite. |
| `log.isErrorEnabled()` … `log.isTraceEnabled()` | (unchanged) | Already SLF4J-compatible. |

A `NoDeps` variant (`CommonsLoggingToSlf4jRecipeNoDeps`) exists for multi-module projects where Lombok lives at a parent level.

## SLF4J cleanup recipes

Pure transformations on existing SLF4J code — no dependency changes. Useful on their own, or compose them after the migration recipes above (which is what `MigrateToSlf4jRecipe` does).

| Recipe | Pattern fixed | Bug class |
| --- | --- | --- |
| **`Slf4jConcatToParameterized`** | `log.info("user " + id + " did " + a)` → `log.info("user {} did {}", id, a)` | Performance: assembles the message string even when the level is disabled. |
| **`ThrowableLastArgumentNoPlaceholder`** | `log.error("failed: {}", e)` → `log.error("failed", e)` | **Stack trace silently lost.** SLF4J consumes `e` via `toString()` because the placeholder count matches the substitution-arg count. |
| **`ConcatThrowableMessage`** | `log.error("failed: " + e.getMessage())` → `log.error("failed: ", e)` | **Stack trace silently lost.** The message includes only `e.getMessage()`, not the trace. |

All three target SLF4J's `log.X(...)` API — they detect the receiver structurally (named `log`, the Lombok `@Slf4j` convention) so they're safe to compose after the `@Slf4j`-adding recipes whose post-conversion calls don't carry resolved SLF4J types.

### `Slf4jConcatToParameterized`

Rewrites SLF4J log calls whose single argument is a string concatenation into the parameterised form:

```java
log.info("user " + userId + " did " + action);
```

becomes

```java
log.info("user {} did {}", userId, action);
```

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.Slf4jConcatToParameterized")
}
```

| Skipped when | Reason |
| --- | --- |
| Receiver isn't named `log` | Heuristic guard — Lombok convention is `log`. |
| Method name isn't an SLF4J level | Only `trace`/`debug`/`info`/`warn`/`error` are touched. |
| Argument isn't a `+`-concatenation | Plain string literals and method calls pass through. |
| Concat is all string literals | Nothing to parameterise (`"a" + "b"` has no substitution slot). |
| Call has 2+ arguments | Already parameterised or already passes a throwable as a trailing arg. |

Why hand-rolled instead of upstream's `org.openrewrite.java.logging.ParameterizedLogging`: upstream's `UsesMethod` precondition matches by bound method type and silently skips post-conversion calls whose LST type info is stale (OpenRewrite doesn't re-parse the LST mid-pipeline).

### `ThrowableLastArgumentNoPlaceholder`

When the placeholder count in an SLF4J message matches the substitution-arg count and the last argument is a `Throwable`, SLF4J binds the throwable to the placeholder via `toString()` instead of the trailing stack-trace slot — the stack trace is silently lost. The recipe drops the trailing `{}` so the throwable lands on the trailing-throwable slot:

```java
log.error("failed: {}", e);              // bug: stack trace lost
log.error("user {} failed: {}", id, e);  // bug: stack trace lost
```

become

```java
log.error("failed", e);                  // stack trace logged
log.error("user {} failed", id, e);      // stack trace logged
```

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.ThrowableLastArgumentNoPlaceholder")
}
```

Trailing ` `, `:`, `,` after the dropped placeholder are trimmed. Escaped `\{}` sequences are recognised and don't count as placeholders. If trimming would yield an empty message, the call is left alone (we'd have to guess at a default).

### `ConcatThrowableMessage`

Rewrites SLF4J log calls of the form

```java
log.error("failed: " + e.getMessage());
```

into

```java
log.error("failed: ", e);
```

— peels the trailing `+ e.getMessage()` off the message string and passes the throwable as a separate argument so SLF4J appends the stack trace. Multi-part LHS chains (`"a " + b + ": " + e.getMessage()`) preserve the leading text verbatim.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.cleanlogging.ConcatThrowableMessage")
}
```

Skipped when the call already has more than one argument (already correct), the right-hand side isn't `Throwable.getMessage()`, or the receiver isn't an `org.slf4j.Logger`. Throwable subtypes (`Exception`, `RuntimeException`, `IOException`, …) all match.

## Logging configuration

The recipes create two files:

- **`src/main/resources/log4j2.xml`** — production config. Non-error levels go to stdout, errors go to stderr (respecting the `System.out` vs `System.err` split), plus a `RollingFile` appender under `./logs/` with daily + 10 MB rollover, gzip compression, and a 10-file retention.
- **`src/test/resources/log4j2-test.xml`** — console-only. Log4j2 automatically prefers this file when tests run, so unit tests don't write to `./logs/` or create rollover files.

Both files are written with `overwriteExisting: false`, so existing configs are left alone.

## Examples

### Simple println

**Before**:
```java
public class MyClass {
    public void greet() {
        System.out.println("Hello World");
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MyClass {
    public void greet() {
        log.info("Hello World");
    }
}
```

### String Concatenation

**Before**:
```java
public class Calculator {
    public void add(int a, int b) {
        System.out.println("Adding " + a + " and " + b);
        int result = a + b;
        System.out.println("Result: " + result);
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Calculator {
    public void add(int a, int b) {
        log.info("Adding {} and {}", a, b);
        int result = a + b;
        log.info("Result: {}", result);
    }
}
```

### Error Logging

**Before**:
```java
public class ErrorHandler {
    public void handleError(Exception e) {
        System.err.println("Error occurred: " + e.getMessage());
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorHandler {
    public void handleError(Exception e) {
        log.error("Error occurred: {}", e.getMessage());
    }
}
```

### Printf Format Strings

**Before**:
```java
public class Formatter {
    public void displayData(String name, int age) {
        System.out.printf("Name: %s, Age: %d%n", name, age);
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Formatter {
    public void displayData(String name, int age) {
        log.info("Name: {}, Age: {}", name, age);
    }
}
```

### java.util.logging

**Before**:
```java
import java.util.logging.Level;
import java.util.logging.Logger;

public class Service {
    private static final Logger logger = Logger.getLogger(Service.class.getName());

    public void run() {
        logger.info("starting");
        logger.severe("boom");
        logger.fine(() -> "verbose: " + heavyCompute());
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("expensive");
        }
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Service {

    public void run() {
        log.info("starting");
        log.error("boom");
        log.debug("verbose: " + heavyCompute());
        if (log.isDebugEnabled()) {
            log.debug("expensive");
        }
    }
}
```

| JUL → SLF4J level | Method | `isLoggable` |
| --- | --- | --- |
| `severe` | `error` | `Level.SEVERE` → `isErrorEnabled()` |
| `warning` | `warn` | `Level.WARNING` → `isWarnEnabled()` |
| `info` | `info` | `Level.INFO` → `isInfoEnabled()` |
| `config` | `debug` | `Level.CONFIG` → `isDebugEnabled()` |
| `fine` | `debug` | `Level.FINE` → `isDebugEnabled()` |
| `finer` | `trace` | `Level.FINER` → `isTraceEnabled()` |
| `finest` | `trace` | `Level.FINEST` → `isTraceEnabled()` |

The hand-rolled `Logger logger = Logger.getLogger(...)` field is removed once nothing else in the class references it, and unused `java.util.logging.*` imports (including `Level` after `isLoggable` is rewritten) are pruned. Lambda-supplier overloads (`logger.fine(() -> "v=" + value)`) are unwrapped to their body expression — SLF4J doesn't take a `Supplier`, the lambda becomes the message string. Block-body lambdas and bare `Supplier` references are left alone (we can't safely flatten them).

### Exception printStackTrace

**Before**:
```java
public class ErrorHandler {
    public void handleError() {
        try {
            riskyOperation();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ErrorHandler {
    public void handleError() {
        try {
            riskyOperation();
        } catch (Exception e) {
            log.error("Exception occurred", e);
        }
    }
}
```

The `printStackTrace(System.err)` and `printStackTrace(System.out)` overloads are handled the same way — the stream argument is dropped because the rewritten `log.error` call routes via the level alone (production `log4j2.xml` maps `ERROR` to `SYSTEM_ERR`).

### Apache Commons Logging

**Before**:
```java
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Service {
    private static final Log log = LogFactory.getLog(Service.class);

    public void boom() {
        try { risky(); } catch (Exception e) {
            log.fatal("game over", e);
        }
        if (log.isFatalEnabled()) {
            log.fatal("noisy");
        }
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Service {

    public void boom() {
        try { risky(); } catch (Exception e) {
            log.error("game over", e);
        }
        if (log.isErrorEnabled()) {
            log.error("noisy");
        }
    }
}
```

### Concat → parameterised SLF4J

**Before**:
```java
@Slf4j
public class Audit {
    public void access(String userId, String resource) {
        log.info("user " + userId + " accessed " + resource);
    }
}
```

**After**:
```java
@Slf4j
public class Audit {
    public void access(String userId, String resource) {
        log.info("user {} accessed {}", userId, resource);
    }
}
```

The parameterised form skips message assembly entirely when the level is disabled.

### Throwable consumed by trailing placeholder

**Before** (silent bug — stack trace is dropped):
```java
@Slf4j
public class Worker {
    public void boom() {
        try { risky(); } catch (Exception e) {
            log.error("failed: {}", e);
        }
    }
}
```

**After** (stack trace logged):
```java
@Slf4j
public class Worker {
    public void boom() {
        try { risky(); } catch (Exception e) {
            log.error("failed", e);
        }
    }
}
```

SLF4J binds the trailing `Throwable` to the stack-trace slot only when the placeholder count is **one less** than the substitution-arg count. When the counts match, the throwable falls into the `{}` and gets `toString()`-substituted — the trace is silently lost.

## Why This Recipe Exists (Clean Code Notes)

These transforms are motivated by a few principles from Robert C. Martin's *Clean Code: A Handbook of Agile Software Craftsmanship*. The specific chapter-and-verse citations below are the general regions where these ideas live in the book — the full arguments are worth reading in context.

**`System.out.println` belongs to throwaway scripts, not production code.** Writing directly to standard output ties a piece of business logic to one destination (the console), one format (a raw string), and one verbosity level (always on). *Clean Code*'s running theme in **Chapter 17 — Smells and Heuristics** is that code should not accumulate the kind of cruft that leaves you guessing: if a class emits diagnostic output, that behaviour should be discoverable, configurable, and replaceable, which means it has to go through a logging framework.

**Mumbling is a code smell.** **Chapter 4 — Comments** uses the word *mumbling* to describe comments written in a hurry, that don't actually say anything to the reader. The same principle applies to log messages: a line like `System.out.println("here 3")` or `log.info("done")` is a mumble — it costs the reader time without paying them anything back. When you convert these calls, it's worth taking a moment to make the message carry real information (what happened, which entity it happened to, and why the reader cares).

**Don't pollute the code with logging infrastructure.** Every hand-rolled `private static final Logger log = LogManager.getLogger(...);` is a line that isn't about the business problem. It also creates a small opportunity for inconsistency — the wrong class reference, the wrong field name, the wrong logger vendor. `@Slf4j` removes that line entirely: the annotation declares intent, Lombok generates the field, and the class body stays focused on what it's *for*. The `ConvertManualLoggerToSlf4jRecipe*` family in this project exists specifically to strip that boilerplate out of projects that already use Log4j2.

**Use the right tool.** Log4j2 gives you levels, layouts, appenders, filters, asynchronous delivery, and structured output. `System.out.println` gives you a string on a stream. The ratio of capability to line-count is enormous, and picking the right abstraction is — in Martin's framing — a defining habit of professional code.

## Troubleshooting

**Recipe not found**: Ensure the dependency coordinates and version in your TOML match exactly.

**Build fails after transformation**: Verify dependencies and `log4j2.xml` were added correctly.

**`build.gradle.kts` looks mangled after the rewrite**: if your original `dependencies { ... }` block was on a single line, OpenRewrite inserts new lines with mixed indentation and a dangling closing `}`. It's cosmetic — the build still resolves. Run your formatter of choice (ktfmt, spotless, or the IDE's reformat) afterwards to clean it up.

**Debug recipes**:
```bash
./gradlew rewriteDiscover          # List available recipes
./gradlew rewriteRun --info        # Verbose output
```

## Resources

- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Lombok @Slf4j](https://projectlombok.org/features/log)
- [Apache Log4j2](https://logging.apache.org/log4j/2.x/)

## For maintainers (and any AI coding agent working in this repo)

**IMPORTANT**: this repo ships four agent skills under `.claude/skills/` (the directory name is a Claude Code convention; other AI coding agents — Cursor, Codex, OpenCode — should be pointed at the same path). They capture the patterns we've converged on and should be invoked rather than re-derived in any future development work. They are:

- **`.claude/skills/new-recipe/`** — **IMPORTANT** when authoring a new OpenRewrite recipe. Covers the `moderneinc/rewrite-recipe-starter` template, the correct `src/main/resources/META-INF/rewrite/` manifest location, visitor structure with package-private helpers, `MethodMatcher` usage, marker-preserving argument-level tree edits (the pattern that keeps Groovy DSL paren-less), YAML composition, and `@Option(required = false)` patterns.
- **`.claude/skills/recipe-testing/`** — **IMPORTANT** when writing or restructuring tests. Covers the two-layer integration + unit test split, `RewriteTest` / `TypeValidation.none()` for Lombok-aware recipes, multi-source `rewriteRun` with `java()` + `buildGradle[Kts]()` + `toml()` + `properties()`, `GradleProject` marker injection for multi-module simulation, and the project-shape matrix-test pattern.
- **`.claude/skills/smoke-test/`** — **IMPORTANT** when designing or extending the pre-release smoke-test procedure. Covers the `/tmp` throwaway-project bootstrap, the `rewriteDryRun` → inspect → `rewriteRun` → `compileJava` cycle, the `include` vs. `includeBuild` distinction, the expected-outcomes table format, and the resolve-by-mavenLocal-coordinates check.

`AGENTS.md` (with `CLAUDE.md` symlinked to it for Claude Code back-compat) is the entry point for anything that's specific to this particular repo (project structure, the publication workflow, the coding standards SpotBugs doesn't catch). If you're tempted to add generic recipe / testing / Gradle / smoke-test guidance to `AGENTS.md`, stop — that content belongs in the corresponding skill.

### Build shape

The reusable build (toolchain, JaCoCo, SpotBugs, integrationTest source set + JDK-21 launcher pin, smokeTest source set + mavenLocal-publish gate, `publishAndReleaseToMavenCentral` `dependsOn("smokeTest")`) lives in `build-logic/` as the `recipe-library` convention plugin lifted from [`fiftiesHousewife/recipescaffold`](https://github.com/fiftiesHousewife/recipescaffold). The project's own `build.gradle.kts` is just identity (group, version, POM) plus a small handful of clean-logging-specific extras (the `cleancode` plugin, Checkstyle strictness, the GitHub Packages publishing repo). Knobs are exposed via `gradle.properties` keys: `recipeLibrary.javaTargetMain`, `recipeLibrary.javaTargetTests`, `recipeLibrary.minLineCoverage`, `recipeLibrary.spotbugsStrict`, `recipeLibrary.failOnStaleDependencies`.

### Authoring new recipes

The project carries a `.recipescaffold.yml` dropfile, so the [recipescaffold](https://github.com/fiftiesHousewife/recipescaffold) JBang CLI works against this checkout:

```bash
# add a Java recipe + test
jbang recipescaffold@fiftiesHousewife/recipescaffold add-recipe \
  --name MyRecipe \
  --display-name "..." \
  --description "..." \
  --package=io.github.fiftieshousewife.cleanlogging   # avoids the .recipes subpackage default

# pre-push gate — runs ./gradlew check integrationTest smokeTest
jbang recipescaffold@fiftiesHousewife/recipescaffold verify-gates

# refresh .claude/skills/ from upstream
jbang recipescaffold@fiftiesHousewife/recipescaffold upgrade-skills [--dry-run]
```

The `--package` flag is currently load-bearing: the CLI's default is `<rootPackage>.recipes`, but clean-logging's v1.0 rename flattened that — recipes sit at `io.github.fiftieshousewife.cleanlogging.*` with no `.recipes.` segment. Always pass `--package=io.github.fiftieshousewife.cleanlogging` when adding new recipes. See `AGENTS.md` § "Authoring new recipes" for the full set of `--type` and `--test-style` options.

---

**Built with OpenRewrite • Lombok • Log4j2**
