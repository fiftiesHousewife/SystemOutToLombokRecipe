# Recipe audit — upstream OpenRewrite vs. our recipes (existing + planned)

## Purpose

Before writing more recipes, map every existing and planned recipe in this
project against what `org.openrewrite.recipe:rewrite-logging-frameworks`
already publishes. Goal: avoid duplicating upstream, identify the genuine
gaps, and lock in what this project's *actual* differentiation is.

## Cross-cutting facts about upstream

- Upstream has rich logging-conversion recipes but **never adds `@Slf4j`** to
  a class. Where it materialises a logger field it writes a directly-declared
  `private static final Logger` via `AddLogger`, configurable by `loggerName`
  / `loggingFramework`.
- Upstream **has no version-catalog awareness** (no `libs.xxx` references in
  Gradle) and **no `requireLombokOnClasspath`-style classpath gating**.
- Upstream's `LoggingFramework` enum supports SLF4J, Log4J1, Log4J2, JUL,
  COMMONS, SYSTEM — Commons Logging is first-class.

## What this project's actual differentiation is

1. **Lombok `@Slf4j` as the destination** for every Logger-field-bearing
   recipe.
2. **Version-catalog awareness** for Gradle (`libs.xxx` references in
   `build.gradle.kts`, entries in `gradle/libs.versions.toml`).
3. **`requireLombokOnClasspath` gating** for multi-module projects where
   only some modules have Lombok on the classpath.

`DirectSlf4jLoggerFieldToLombok` (Tier 1, write-from-scratch) is the
keystone primitive. It lets us ride on top of upstream's much larger
catalogue (JUL→SLF4J, Commons→SLF4J, Log4j1→SLF4J, Log4j2→SLF4J — all
landing on a direct `Logger` field) and finish the job by converting that
direct field to `@Slf4j`. Without it, every upstream recipe stops one step
short of where this project promises to land.

---

## Existing recipes in this repo

| Recipe | Closest upstream | Verdict | Notes |
|---|---|---|---|
| `SystemOutToSlf4j` | `o.o.j.l.SystemPrintToLogging` (composes `SystemOutToLogging` + `SystemErrToLogging` + `PrintStackTraceToLogError`) | **KEEP** | Lombok @Slf4j integration + classpath gate is our differentiation. Upstream writes a direct `Logger` field. |
| `AddLombokSlf4jAnnotation` | None. `ChangeLombokLogAnnotation` only converts between existing Lombok annotations. | **KEEP** | Unique. |
| `PrintStackTraceToLog` | `o.o.j.l.PrintStackTraceToLogError` | **REPLACE with thin wrapper** | Upstream is more capable (handles `printStackTrace(System.err/out)` for free, supports `addLogger`/`loggerName`/`loggingFramework`). Wrap with `loggingFramework=SLF4J, loggerName=log, addLogger=false` plus the classpath gate. Picks up Tier 1's `PrintStackTraceWithStream` for free. |
| `JulToSlf4j` | `o.o.j.l.slf4j.JulToSlf4j` (composite of 7+ recipes incl. `JulGetLoggerToLoggerFactory`, `JulParameterizedArguments`, `JulToSlf4jSimpleCallsWithThrowableRecipes`) | **COMPOSE wrapper** | Upstream is more thorough (lambda suppliers, isLoggable). Upstream lands SLF4J `Logger` field, **not `@Slf4j`**. Run upstream → then our new `DirectSlf4jLoggerFieldToLombok`. Drop our hand-rolled level mapping. |
| `ConvertManualLoggerToSlf4j` | None directly. | **KEEP** as Log4j2-Logger-field special case | Reframe as a special case of `DirectSlf4jLoggerFieldToLombok` once that exists. |
| `ParameterizeStringConcat` (just written, **deleted in this audit**) | `o.o.j.l.ParameterizedLogging` | **DELETED** | Upstream handles concat, preserves trailing Throwable, refuses Throwable-inside-concat, has `methodPattern` + `removeToString` options. Use upstream with `methodPattern: "org.slf4j.Logger *(..)"`. |

---

## Tier 1 (was 6 from-scratch recipes — becomes 2 from-scratch + 3 wrappers + 1 deletion)

| Planned recipe | Upstream | Verdict |
|---|---|---|
| ParameterizeStringConcat | `ParameterizedLogging` | **USE-UPSTREAM** (already deleted ours). |
| ThrowableLastArgumentNoPlaceholder | `o.o.j.l.slf4j.CompleteExceptionLogging` | **USE-UPSTREAM**. Strips trailing `{}` when last arg is a Throwable; idempotent if already correct. Doesn't cover Log4j-style `error(e, "msg")` — write a tiny supplemental recipe only if seen in the wild. |
| **ConcatThrowableMessage** (`"failed: " + e.getMessage()` → `log.error("failed", e)`) | None. `CompleteExceptionLogging` only handles `getMessage()` as a *separate* argument, not concatenated into a `J.Binary`. | **WRITE-FROM-SCRATCH** — genuine gap, highest-value addition. |
| CommonsLoggingToSlf4j | `o.o.j.l.slf4j.CommonsLogging1ToSlf4j1` | **USE-UPSTREAM**, then chain our `DirectSlf4jLoggerFieldToLombok` to land on `@Slf4j`. |
| **DirectSlf4jLoggerFieldToLombok** | None. | **WRITE-FROM-SCRATCH** — keystone primitive; absorbs `ConvertManualLoggerToSlf4j` as a special case. |
| PrintStackTraceWithStream | `PrintStackTraceToLogError` | **SUBSUMED** — covered when we adopt upstream for `PrintStackTraceToLog`. |

---

## Tier 2 (mostly from-scratch — upstream has very little in this space)

| Planned | Upstream | Verdict |
|---|---|---|
| StaticFinalLoggerField | `ChangeLoggersToPrivate` is *private only*, not static/final. | **WRITE-FROM-SCRATCH** (or compose: upstream private + ours static-final). |
| CanonicalLoggerFieldName (`LOG`/`LOGGER` → `log`) | None. `LoggersNamedForEnclosingClass` is unrelated (it normalises `getLogger(X.class)` arg). | **WRITE-FROM-SCRATCH**. |
| RemoveUnusedLoggerField | None. | **WRITE-FROM-SCRATCH** — generalise the cleanup currently embedded in `JulToSlf4jVisitor`. |
| NoSystemConsoleInLibraryCode | `SystemPrintToLogging` rewrites *to* a logger; doesn't have an opt-in flag-and-remove mode. | **WRITE-FROM-SCRATCH** (very thin; precondition on archetype option). |

## Tier 3 (entirely from-scratch — no upstream MDC/anti-pattern recipes)

| Planned | Upstream | Verdict |
|---|---|---|
| EmptyCatchBlock (log-warn or rethrow) | `o.o.staticanalysis.EmptyBlock` (removes), `RenameExceptionInEmptyCatch`. Neither logs/rethrows. | **WRITE-FROM-SCRATCH** for the "log a warning" variant. |
| LogAndThrow | `o.o.staticanalysis.CatchClauseOnlyRethrows` collapses pure-rethrow catches; doesn't address log-then-throw. | **WRITE-FROM-SCRATCH**. |
| MdcPutWithoutRemove → `MDCCloseable` | **No MDC recipe anywhere upstream** (verified across rewrite-logging-frameworks and rewrite-static-analysis). | **WRITE-FROM-SCRATCH** — clear greenfield. |

---

## Smoke-test policy when adopting upstream

**No smoke tests get deleted when we adopt an upstream recipe — they get
*enhanced*.** Every existing `src/smokeTest/` cell stays in place and continues
to assert the end-to-end behaviour we promise. When a Java recipe is replaced
by an upstream wrapper, the smoke tests become the safety net that confirms
upstream behaviour matches our published contract.

For each upstream adoption:

- **Keep** all existing smoke cells covering the behaviour now delegated.
- **Add** at least one new smoke cell exercising any new surface upstream gives
  us for free (e.g., `printStackTrace(System.err)` once we adopt
  `PrintStackTraceToLogError`).
- **Pin** the upstream version in `gradle/libs.versions.toml` and bump
  intentionally — an upstream recipe changing behaviour out from under us is
  the failure mode the smoke tests catch.

The unit-test-level `RewriteTest` suite for a wrapped recipe should drop
duplicative cases (those are upstream's job to cover) but must keep the cases
that prove our wrapper-specific behaviour: classpath gating, version-catalog
references, Lombok @Slf4j integration.

---

## Recommended ordering for v1.0

1. **Build `DirectSlf4jLoggerFieldToLombok`** — the keystone. Until this
   exists, no upstream wrapping pays off because the result lands on a direct
   `Logger` field instead of `@Slf4j`.
2. **Build `ConcatThrowableMessage`** — genuine gap, highest-bug-frequency
   addition.
3. **Replace `PrintStackTraceToLog`** with a thin wrapper around upstream
   `PrintStackTraceToLogError`. Adopts `PrintStackTraceWithStream` for free.
4. **Refactor `JulToSlf4j`** to compose upstream `o.o.j.l.slf4j.JulToSlf4j` +
   `DirectSlf4jLoggerFieldToLombok`. Drop hand-rolled level mappings.
5. **Add `ThrowableLastArgumentNoPlaceholder`** as a thin wrapper around
   upstream `CompleteExceptionLogging`.
6. **Add `CommonsLoggingToSlf4j`** as a wrapper composing upstream
   `CommonsLogging1ToSlf4j1` + `DirectSlf4jLoggerFieldToLombok`.
7. **Wire `ParameterizedLogging`** (upstream) into the composed YAML for
   `MigrateToSlf4j` so `log.info("a " + b)` shapes get parameterized after our
   conversions run.
8. **Reframe `ConvertManualLoggerToSlf4j`** as a Log4j2-specific special case
   of `DirectSlf4jLoggerFieldToLombok` — keep the recipe ID for backward
   compat; share visitor code.

After all eight steps land cleanly with smoke tests green, do the rename to
`clean-logging:1.0`.

Tier 2 and Tier 3 follow in v1.x and v2.0 — they're almost entirely
from-scratch because upstream doesn't cover that ground.

---

## Net effect on planned implementation effort

- **Tier 1** drops from 6 from-scratch recipes to **2 from-scratch + 3
  wrappers + 1 deletion**.
- **Existing recipes**: 2 stay as-is (`SystemOutToSlf4j`,
  `AddLombokSlf4jAnnotation`), 2 become wrappers (`PrintStackTraceToLog`,
  `JulToSlf4j`), 1 gets reframed (`ConvertManualLoggerToSlf4j`), 1 is deleted
  (`ParameterizeStringConcat`).
- **Tier 2**: stays mostly from-scratch (upstream gives us almost nothing
  here).
- **Tier 3**: entirely from-scratch.

---

## Upstream source references

- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/ParameterizedLogging.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/PrintStackTraceToLogError.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/SystemPrintToLogging.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/slf4j/CompleteExceptionLogging.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/slf4j/CommonsLogging1ToSlf4j1.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/ChangeLombokLogAnnotation.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/blob/main/src/main/java/org/openrewrite/java/logging/ChangeLoggersToPrivate.java`
- `https://github.com/openrewrite/rewrite-logging-frameworks/tree/main/src/main/resources/META-INF/rewrite` — composed YAML recipes incl. `Slf4jBestPractices`, `slf4j.yml`.

Verify each source before depending on it: pin the exact upstream version in
the version catalog and read the recipe's `@Option`s before composing it.
