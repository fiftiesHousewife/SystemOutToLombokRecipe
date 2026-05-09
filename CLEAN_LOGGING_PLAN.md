# Clean Logging — rename + scope expansion plan

---

## Status snapshot — 2026-05-09

**Tier 1 complete and shipped as 0.9** (commits up to `1ebf501` on `main`,
8 ahead of `origin/main`, not yet pushed/published).

**Decisions locked for the v1.0 rename** (operator confirmation 2026-05-09):

1. Group ID stays at `io.github.fiftieshousewife`.
2. **No backwards compatibility / no deprecation aliases.** Old artifact
   `system-out-to-lombok-log4j:0.9` stays published on Central (Maven
   Central is immutable); it just stops getting updates. New artifact
   `clean-logging:1.0` ships with the new package + recipe IDs only.
   Migration cost for existing users: change two strings in their build
   (`rewrite("…:system-out-to-lombok-log4j:0.9")` →
   `rewrite("…:clean-logging:1.0")` plus
   `activeRecipe("io.github.fiftieshousewife.X")` →
   `activeRecipe("io.github.fiftieshousewife.cleanlogging.X")`).
3. Naming casing locked: `clean-logging` (artifact / YAML manifest) /
   `cleanlogging` (Java package) / `CleanLogging` (Java class prefix
   where needed) / "Clean Logging" (prose).
4. Ship a `MigrateToCleanLoggingRecipe` one-shot composed recipe
   covering Tier 1 + the existing `MigrateToSlf4j` chain.

**Operator prerequisites BEFORE the rename starts** (Phase 0 below):

- Push 0.9 commits + publish 0.9 to Maven Central + tag `v0.9`. The
  rename should land on top of a published 0.9 so users on the old
  artifact have a stable terminal version to target.
- Confirm Sonatype namespace `io.github.fiftieshousewife` accepts the
  new `clean-logging` artifact name (no separate Central setup needed
  since the group ID isn't changing).

---

## Detailed execution plan

### Phase 0 — Operator: ship 0.9, then green-light v1.0 rename

```
git push origin main
./gradlew publishAndReleaseToMavenCentral
git tag v0.9 && git push origin v0.9
```

Verify on Central before kicking off Phase 1. The publish task
`dependsOn("smokeTest")` so the structural gate fires automatically.

### Phase 1 — Mechanical rename (Claude, one or two commits)

The rename is purely textual but spans ~80 files. Audit (run
2026-05-09) returned the full list — every file under
`src/{main,test,integrationTest,smokeTest}/java/io/github/fiftieshousewife/recipes/`
plus the YAML manifest, build files, and docs.

Order matters because a partial rename breaks compilation:

1. **Java package move + import rewrite** — the largest mechanical step.
   - Move `src/{main,test,integrationTest}/java/io/github/fiftieshousewife/recipes/**`
     → `…/cleanlogging/**` (file moves preserving directory structure).
   - Sed (or IDE rename) every `import io.github.fiftieshousewife.recipes.X;`
     → `import io.github.fiftieshousewife.cleanlogging.X;` across all .java files
     (main, test, integrationTest, smokeTest, matrix subdir).
   - Sed `package io.github.fiftieshousewife.recipes;` → `package io.github.fiftieshousewife.cleanlogging;`
     in every moved file.
   - Sed every `io.github.fiftieshousewife.recipes.X` (FQN string references —
     YAML, integration-test recipe-ID strings, smoke scaffolders) → `…cleanlogging.X`.
   - Smoke scaffolders (`SmokeProject.java`, `ProjectShapeScaffolder.java`,
     `RecipeResolutionSmokeTest.java`) hardcode the artifact name in the
     scaffolded `build.gradle.kts` — `system-out-to-lombok-log4j` → `clean-logging`.
2. **Top-level recipe IDs in YAML** — `io.github.fiftieshousewife.SystemOutToSlf4jRecipe`
   etc. → `io.github.fiftieshousewife.cleanlogging.SystemOutToSlf4jRecipe`. The
   YAML's `recipeList:` entries that reference leaf recipe FQNs all flip too.
   Test files' `activateRecipes(...)` calls use these IDs — sweep them in the
   same commit.
3. **YAML manifest rename** —
   `src/main/resources/META-INF/rewrite/system-out-to-lombok.yml`
   → `src/main/resources/META-INF/rewrite/clean-logging.yml`. Also use
   `git mv` so the rename shows as such, not as delete + add.
4. **Build files**:
   - `settings.gradle.kts:1` — `rootProject.name = "system-out-to-lombok-log4j"` →
     `rootProject.name = "clean-logging"`.
   - `build.gradle.kts:313` — `coordinates(group.toString(), "system-out-to-lombok-log4j", version.toString())`
     → `coordinates(group.toString(), "clean-logging", version.toString())`.
   - `build.gradle.kts:10` — `version = "0.9"` → `version = "1.0"`.
   - `build.gradle.kts:286` (and surrounding `pom { url, scm.connection }`) —
     update GitHub URLs from `SystemOutToLombokRecipe` → `clean-logging`.
5. **README** — full headline + intro rewrite. Drop "System.out to Lombok @Slf4j"
   in favour of "Clean Logging — automated SLF4J/Lombok hygiene for Java
   codebases" (or similar). Lead the intro with the spectrum of patterns now
   that Tier 1 is shipped. Update the at-a-glance table to use new recipe IDs.
6. **BACKLOG.md** — add a 1.0 entry under Shipped with the rename + Tier 1
   summary; remove the "clean-logging v1.0 rename" item from "Queued for next
   release" since it's done.
7. **CLEAN_LOGGING_PLAN.md** — mark Phase 1 done in this status snapshot;
   add a final 1.0-shipped status entry.
8. **SMOKE_TEST.md §2a project-shape templates** — every reference to
   `system-out-to-lombok-log4j` artifact → `clean-logging`.
9. **AGENTS.md** — only references the artifact in the publication-workflow
   §; verify with grep, update if hit.

### Phase 2 — `MigrateToCleanLoggingRecipe` (Claude, one commit)

Compose `MigrateToSlf4jRecipe` + the Tier 1 SLF4J cleanups (`Slf4jConcatToParameterized`,
`ThrowableLastArgumentNoPlaceholder`, `ConcatThrowableMessage`) into a single
top-level YAML recipe. `MigrateToSlf4jRecipe` already has the parameterizer +
throwable-placeholder cleanup wired into its pipeline (commits `ca3c973` /
`0050d8f`), so `MigrateToCleanLoggingRecipe` is `MigrateToSlf4jRecipe` plus
the `CommonsLoggingToSlf4j` step prepended (so legacy Commons Logging gets
folded onto `@Slf4j` before the rest of the chain runs).

Tags: `logging`, `lombok`, `slf4j`, `log4j2`, `jul`, `commons-logging`.

A `NoDeps` variant for multi-module projects.

### Phase 3 — Verification (Claude)

```
./gradlew-claude check          # unit + integration
./gradlew-claude smokeTest      # 17 cells
```

Both must be green. The smoke env fix in commit `a177aa2` keeps the
operator-laptop JDK 25 quirk from biting smokeTest. The smoke
scaffolders now reference `clean-logging`, so the publish-to-mavenLocal
+ resolve cycle exercises the new artifact end-to-end.

### Phase 4 — Operator: publish v1.0

```
git push origin main
./gradlew publishAndReleaseToMavenCentral
git tag v1.0 && git push origin v1.0
```

### Phase 5 — Operator: GitHub repo rename

GitHub Settings → repo rename `SystemOutToLombokRecipe` → `clean-logging`.
Auto-redirect preserved by GitHub for the old URL. Update `pom.url` /
`scm.connection` already done in Phase 1 step 4.

### Phase 6 — Operator: local working tree

```
mv ~/Claude ~/clean-logging
```

Knock-on effects:
- IDE workspace path needs re-pointing.
- Claude Code session memory at
  `~/.claude/projects/-Users-pippanewbold-Claude/...` — Claude Code
  will auto-create a new path (`-Users-pippanewbold-clean-logging`)
  on next session start. The existing memory at the old path won't be
  visible to the new session unless the directory is symlinked or
  contents are migrated.
- `gradlew-claude` wrapper at the repo root keeps working (relative paths).

### Phase 7 — Operator: JBang template repo

`~/openrewrite-recipe-template-fhw/tests/ci-smoke.sh` references
`system-out-to-lombok` as an example cell — update to `clean-logging`
or replace with a different exemplar.

---

## Audit results — files touched by Phase 1 (run 2026-05-09)

86 files reference `system-out-to-lombok` and/or `fiftieshousewife.recipes`:

- 1 YAML manifest (`system-out-to-lombok.yml`) — renamed.
- 50 main + test + integrationTest .java files under `…/recipes/` — moved
  to `…/cleanlogging/` with package + imports updated.
- 4 smokeTest .java files — `…/smoketest/` package stays (smoke scaffolders
  aren't part of the recipe library); only the hardcoded artifact name
  changes inside them.
- 3 matrix test files (`matrix/GroovyDslMatrixTest`, `matrix/KotlinDslMatrixTest`,
  `matrix/MatrixTestSupport`) — moved + imports updated.
- 2 build files (`build.gradle.kts`, `settings.gradle.kts`).
- 5 docs (README.md, BACKLOG.md, CLEAN_LOGGING_PLAN.md, SMOKE_TEST.md, AGENTS.md).

The rename can land in either one large commit (single mental unit, easier
git history) or two (Java package move; everything else). Single commit
recommended unless review pressure dictates otherwise.

## Risks / known gotchas

- **Don't restart the session mid-rename** — Phase 1 has to complete
  in one pass or the codebase is left non-compiling.
- **Run `./gradlew check` after the package move and BEFORE renaming
  the YAML manifest / recipe IDs** — catches package-level breakage
  early, separate from precondition / activeRecipe-ID resolution
  failures that surface later.
- **Smoke test scaffolders compile against runtime classpath** — the
  new artifact name in their hardcoded `rewrite("…:clean-logging:%s")`
  line must match the build's `coordinates(...)` exactly or the
  smoke project fails resolution.
- **`MigrateToCleanLoggingRecipe` precondition gap** — same risk as
  the parameterizer / throwable-placeholder recipes: hand-roll
  detection structurally on `log` receiver name; don't rely on
  `UsesType<org.slf4j.Logger>` post-conversion.
- **GitHub repo rename redirect** — works for `git remote` URLs
  (auto-redirect for ~1 year per GitHub policy), but Sonatype's
  scm-connection metadata in published 1.0 POMs is locked at publish
  time. Phase 1 step 4 must update `scm.connection` before publish or
  the published POM points at the dead old URL.

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

### Tier 1 — core "clean logging" recipes (v1.0 must-have) — **COMPLETE 2026-05-09**

| Recipe | Behaviour | Status |
|---|---|---|
| **Slf4jConcatToParameterized** (was ParameterizeStringConcat) | `log.info("User " + id + " created")` → `log.info("User {} created", id)` | Shipped (commit `ca3c973`). Hand-rolled, not upstream's `ParameterizedLogging` — upstream's `UsesMethod` precondition matches by bound type and skips post-conversion calls whose LST type info is stale. Wired into `MigrateToSlf4jRecipe`. |
| **ThrowableLastArgumentNoPlaceholder** | `log.error("failed: {}", e)` → `log.error("failed", e)` | Shipped (commit pending). Drops the trailing `{}` when SLF4J would otherwise consume the Throwable via `toString()` and silently drop the stack trace. Receiver detection structural (`log` name) so it composes after `@Slf4j`-adding recipes. Wired into `MigrateToSlf4jRecipe` after the parameterizer. |
| **ConcatThrowableMessage** | `log.error("failed: " + e.getMessage())` → `log.error("failed", e)` | Shipped (commit `d7d6038`). |
| **CommonsLoggingToSlf4j** | Same shape as `JulToSlf4j`, different source framework | Shipped (commit `c32269e`). Includes `fatal`/`isFatalEnabled` rename to `error`/`isErrorEnabled` (SLF4J has no fatal level). |
| **DirectSlf4jLoggerFieldToLombok** | `private static final Logger log = LoggerFactory.getLogger(...)` → `@Slf4j` | Shipped (commit `6988557`). Plus shared-base extraction (`dbec180`) consolidates this with `ConvertManualLoggerToSlf4j` and `CommonsLoggingToSlf4j`. |
| **PrintStackTraceWithStream** | `e.printStackTrace(System.err)` → `log.error(throwable)` | **Already covered by `PrintStackTraceToLog`** — its matcher is `printStackTrace(..)` so the stream overloads are caught and the stream argument is dropped. Tests `convertsPrintStackTraceWithSystemErr` and `convertsPrintStackTraceWithSystemOut` already pass. No separate recipe needed. |

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
