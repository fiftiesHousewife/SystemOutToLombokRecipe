# Clean Logging — rename + scope expansion plan

## Why

Currently published as `system-out-to-lombok-log4j` v0.8. The artifact name
undersells the scope and constrains where the project can grow. **"Clean
Logging"** reframes it as the Bob-Martin-style logging-hygiene companion to
the existing `cleancode` Gradle plugin: take any legacy Java codebase, end
up with parameterized SLF4J calls, sane logger fields, no stack-trace-eating
mistakes, and no `System.out` survivors.

This document covers two things that need to ship together:

1. **Rename mechanics** (Part A) — the mostly-mechanical ripple.
2. **Scope expansion** (Part B) — what actually justifies the rename.

Without the scope expansion, the rename is just a relabel of v0.8. Don't
ship one without the other.

---

## Part A — rename mechanics

### Maven coordinates (one-way door)

| | |
|---|---|
| **Old** | `io.github.fiftieshousewife:system-out-to-lombok-log4j:0.8` |
| **New** | `io.github.fiftieshousewife:clean-logging:1.0` |

Constraints:

- Old artifact stays published — Maven Central does not allow unpublish.
- Ship one final v0.9 of the old artifact whose composed YAML aliases the
  new recipe IDs, with a deprecation notice. Existing consumers get a
  migration path; new consumers go direct.
- Group ID stays the same.

### Java package

`io.github.fiftieshousewife.recipes` → `io.github.fiftieshousewife.cleanlogging`

Mostly a mechanical IDE rename across ~30 files. One non-mechanical spot:
integration tests have hard-coded `Environment.scanRuntimeClasspath(
"io.github.fiftieshousewife")` — that keeps working at the parent package,
but every recipe ID changes.

### Recipe IDs (the other one-way door)

| | |
|---|---|
| **Old form** | `io.github.fiftieshousewife.SystemOutToSlf4j` |
| **New form** | `io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4j` |

Recipe IDs are part of the public contract — composing recipes call them by
FQN. The deprecation 0.9 keeps old IDs alive as YAML aliases pointing at
the new package.

### Composed recipe YAML

`src/main/resources/META-INF/rewrite/system-out-to-lombok.yml`
→ `clean-logging.yml`

### Documentation

- **README**: full headline + intro rewrite to lead with "clean logging" and
  list both the `System.out` and the `Logger` modernization paths.
- **BACKLOG.md**: add a "v1.0 rename" entry in Active.
- **SMOKE_TEST.md**: §2a project-shape templates reference the artifactId;
  update.
- **CLAUDE.md**: header doesn't name the old artifact; safe.

### Project directory + GitHub repo

- Local dir `~/Claude` → `~/clean-logging` (knock-on: memory paths under
  `~/.claude/projects/-Users-pippanewbold-Claude/...` and absolute-path
  references in any local scripts).
- GitHub repo `SystemOutToLombokRecipe` → `clean-logging` (auto-redirect
  preserved).
- `pom.url` / `scm.connection` etc. update at `build.gradle.kts:286,301-303`.

### JBang template project

`~/openrewrite-recipe-template-fhw` is generic (template for *any* recipe
project) and stays. But its `tests/ci-smoke.sh` example cell references
`system-out-to-lombok` — update.

---

## Part B — scope expansion (what justifies the rename)

### Tier 1 — core "clean logging" recipes (v1.0 must-have)

| Recipe | Behaviour | Notes |
|---|---|---|
| **ParameterizeStringConcat** | `log.info("User " + id + " created")` → `log.info("User {} created", id)` | Reuses `StringConcatDecomposer` + `LogCallTemplate`. Highest-value addition. |
| **ThrowableLastArgumentNoPlaceholder** | `log.error("failed: {}", e)` → `log.error("failed", e)`; also Log4j-style `log.error(e, "msg")` | Detects Throwable used as substitution arg. Real bug, easy detection. |
| **ConcatThrowableMessage** | `log.error("failed: " + e.getMessage())` → `log.error("failed", e)` | Highest "real bug" frequency in the wild — drops stack trace silently today. |
| **CommonsLoggingToSlf4j** | Same shape as `JulToSlf4j`, different source framework | Lots of legacy commons-logging out there. |
| **DirectSlf4jLoggerFieldToLombok** | `private static final Logger log = LoggerFactory.getLogger(...)` → `@Slf4j` | Generalizes `ConvertManualLoggerToSlf4j` beyond Log4j2. |
| **PrintStackTraceWithStream** | `e.printStackTrace(System.err)` → `log.error(throwable)` | Sibling of existing `PrintStackTraceToLog`. |

### Tier 2 — logger hygiene (v1.x, additive)

| Recipe | Notes |
|---|---|
| **StaticFinalLoggerField** | Promote instance loggers to `private static final`. |
| **CanonicalLoggerFieldName** | Rename `LOG`/`LOGGER`/`l` → `log` (Lombok convention). `@Option` for the chosen name. |
| **RemoveUnusedLoggerField** | Generalize the JUL-specific cleanup that already exists. |
| **NoSystemConsoleInLibraryCode** | Off by default; on for `library` project archetype. |

### Tier 3 — anti-patterns (v2.0, opinionated, opt-in)

| Recipe | Notes |
|---|---|
| **EmptyCatchBlock** — flag; suggest `log.warn` or rethrow | High FP risk on test code; `@Option` for exclusions. |
| **LogAndThrow** — collapse `log.error(...); throw e;` | Strong opinion; expect pushback. |
| **MdcPutWithoutRemove** → migrate to `MDCCloseable` try-with-resources | Real bug class (cross-request log pollution). |

### Tier 4 — config / infrastructure (separate plugin, not recipes)

These don't fit the recipe model well — heuristic-driven or
configuration-shaped. Candidates for a sister Gradle plugin in the
`cleancode` family rather than the OpenRewrite recipe project:

- **Logback / log4j2 XML normalization** (async appenders, JSON encoder,
  rotation defaults).
- **PII-in-log detection** — heuristic scanner; high FP rate.
- **Log-level appropriateness** — opinionated linter.

---

## Phasing

| Version | Scope | Effort |
|---|---|---|
| **v0.9** (final old name) | Deprecation aliases pointing at v1.0 IDs | trivial |
| **v1.0** (rename release) | Existing 4 transformations + Tier 1 (6 recipes) + dependency-management primitives unchanged | ~2–3 weeks |
| v1.1 – v1.3 | Tier 2 incrementally | ~3–5 days each |
| v2.0 | Tier 3 under `@Option`s | ~1 week + design notes |
| ⊥ | Tier 4 split out into companion plugin if pursued | separate project |

---

## Open questions

1. **Group ID change?** Stay at `io.github.fiftieshousewife` or move to a
   generic `org.cleanlogging` / similar? Implication if the project grows
   beyond this user.
2. **One-shot composed recipe.** Ship `MigrateToCleanLogging` that runs
   all Tier 1 + Tier 2 in order? Existing `MigrateToSlf4j` already sets
   the precedent — probably yes.
3. **Naming convention.** Lock the casing now: `CleanLogging` (Java
   identifiers), `clean-logging` (artifact), `cleanlogging` (package),
   "Clean Logging" (prose). Reference from the recipe-naming convention.
4. **Tier 3 default.** Off by default with explicit `@Option` opt-in, or
   shipped as separate top-level recipes for users to compose? The latter
   is more honest about opinionatedness.

---

## Suggested execution order

1. Decide final scope (this doc).
2. Tier 1 implementation against the current `system-out-to-lombok-log4j`
   project — prove the recipes work *before* the rename.
3. Mechanical rename pass (package, artifactId, recipe IDs, YAML, docs)
   in one big commit.
4. Publish v0.9 of the old artifact with deprecation aliases.
5. Publish v1.0 of `clean-logging`.
6. Tier 2 onward.

If Tier 1 doesn't land cleanly the rename should not ship — the artifact
name would be writing a check the recipes don't cash.
