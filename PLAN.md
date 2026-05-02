# Plan: outstanding work after Release 0.7 prep

This is the picked-up-where-we-left-off plan. Each item is self-contained: goal, concrete steps, acceptance criteria, effort, dependencies, and starting file paths. A new session can pick any item without needing prior conversation context.

For the broader picture see `BACKLOG.md` (Shipped / Active / Parked); for the release procedure see `CLAUDE.md` "Publication workflow".

State at time of writing:
- `main` is at `82e2f3b` on origin (CI green).
- Local commit `3cb0a5c` ("Release 0.7") is committed but not pushed. Version bumped 0.6 → 0.7. Working tree otherwise clean.
- All gates green: `./gradlew check integrationTest smokeTest`.

---

## 1. Push the 0.7 release commit

**Goal.** Get `3cb0a5c` off the local laptop and onto `origin/main`.

**Steps.**
1. `git status` — confirm clean working tree, only `3cb0a5c` ahead of origin.
2. `git push origin main`.
3. Watch CI: `gh run list --limit 1 --branch main`. Expect green within ~5 min.

**Acceptance.** `origin/main` at `3cb0a5c`; CI green.

**Effort.** ~5 min.

**Dependencies.** None.

**Risk.** Reversible — push is safe; no Maven Central publish triggered by the push. The version bump alone doesn't ship anything.

---

## 2. Cut the 0.7 release (publish + tag)

**Goal.** Ship the two real bug fixes that 0.7 contains (see `BACKLOG.md` 0.7 entry) to Maven Central so users on `MigrateToSlf4jRecipeNoDeps` / `SystemOutToSlf4jRecipeNoDeps` / `ConvertManualLoggerToSlf4jRecipe` stop getting uncompilable Java.

**One-way door.** `publishAndReleaseToMavenCentral` is irreversible — once a version is on Central, it can't be unpublished. Don't do this without operator confirmation.

**Pre-publish gates** (all should pass cleanly before invoking publish):
1. **`./gradlew check integrationTest`** — should be green from the push above.
2. **`./gradlew smokeTest`** — automated 9-cell single-module matrix. Wired into `publishAndReleaseToMavenCentral` as a hard `dependsOn`, so this runs automatically when you publish — but it's worth running on its own first so a smoke failure is debuggable in isolation, not as a publish-task fail. ~2 min.
3. **`SMOKE_TEST.md` §2a — manual** — six multi-module / `build-logic` / `includeBuild` templates the runner doesn't yet automate (Phase 2 backlog item below). Each is a `/tmp` bootstrap → `dryRun` → inspect patch → `Run` → `compileJava` cycle. ~10 min total. Stop and diagnose if any cell's patch looks wrong or any `compileJava` fails.
4. **`SMOKE_TEST.md` §3 — manual** — `./gradlew publishToMavenLocal`, then point a fresh `/tmp` smoke project at the artifact via Maven coordinates (not `files(...)`) and walk a `rewriteRun` + `compileJava` cycle. Catches POM / Gradle module metadata bugs the in-process tests miss. ~5 min.

**Steps for the publish itself.**
1. Walk gates 1–4 above. If any fails, stop and fix.
2. `./gradlew publishAndReleaseToMavenCentral` — needs Sonatype signing creds in env / `~/.gradle/gradle.properties`. Failure with missing creds is loud.
3. `git tag v0.7 && git push origin v0.7`.
4. Spot-check the artifact on Central: `https://repo1.maven.org/maven2/io/github/fiftieshousewife/system-out-to-lombok-log4j/0.7/` should list the JAR + POM + sources within ~30 min of publish.

**Acceptance.** 0.7 resolvable from `mavenCentral()` by coordinates; tag pushed; users running an upgrade pull the bug fixes.

**Effort.** ~30 min (smoke walks + publish + tag).

**Dependencies.** Item 1 (push) done first.

---

## 3. Smoke automation Phase 2 — automate `SMOKE_TEST.md` §2a templates

**Goal.** Six multi-module / `build-logic` / `includeBuild` templates currently walked by hand at release time become `./gradlew smokeTest` cells. Eliminates the manual gate at item 2, step 3.

**The templates** (per `SMOKE_TEST.md` §2a):
- A — multi-module Kotlin DSL (root + `app` + `lib` subprojects)
- B — multi-module Groovy DSL (paren-less idiom regression check)
- C — Kotlin with `include("build-logic")` subproject
- D — Groovy with `include("build-logic")` subproject (paren-less)
- E — Kotlin composite build (`includeBuild("build-logic")`)
- F — Groovy composite build (`includeBuild("build-logic")`)

**Architecture.** Reuse the Phase 1 runner (`src/smokeTest/java/io/github/fiftieshousewife/smoketest/`):
- `SmokeVariant` model already exists; either extend it with a `topology` enum (SINGLE / MULTI / BUILD_LOGIC_SUBPROJECT / COMPOSITE) or introduce a parallel `MultiModuleSmokeVariant` / `SmokeProject` lineage. Recommend the former — keeps the matrix tabular.
- `SmokeProject.scaffold()` currently writes a single-module layout. Either branch on topology there, or introduce `SmokeProjectScaffolder` strategies — one per template. Strategy classes are cleaner if templates diverge significantly (Groovy DSL vs Kotlin DSL build files differ enough that this matters).
- `GradleRunner` is already topology-agnostic; reuse as-is.
- For composite builds (E, F), each test invocation needs to run the recipe in BOTH the outer build AND the included build (`build-logic`). Add an `invocations` axis to the variant, or a list of subdirectories where `gradlew rewriteRun` should run.

**Steps.**
1. Read `SMOKE_TEST.md` §2a carefully — each template has a specific layout and a specific expected outcome (catalog seeded vs not, paren-less preserved or not). Copy the expected-outcomes table into the SmokeTest's assertion matrix.
2. Decide: extend `SmokeVariant` with a `topology` enum, or split into a `MultiModuleSmokeVariant` record with a `List<Module>` of `(path, build-script-content)` pairs. The record-with-list approach is simpler and more flexible for the six template variants. Pick one.
3. Implement Template A first — it's the simplest multi-module case. Get it green before adding the others.
4. Implement Templates B–F one at a time. After each, `./gradlew smokeTest` should grow by one passing cell.
5. Once all six are green, edit `SMOKE_TEST.md` §2a to say "automated by `./gradlew smokeTest` — see `src/smokeTest/`; the manual procedure below is the reference for adding new templates". Don't delete the manual procedure — it documents the layout each template represents.
6. Update the `smoke-test` skill at `.claude/skills/smoke-test/SKILL.md` to say multi-module templates are also runner-driven now, not bootstrap-only.
7. Update the `BACKLOG.md` Active "Smoke-test automation Phase 2" entry to Shipped, and the §3 round-trip remains the only manual pre-publish step.

**Acceptance.** `./gradlew smokeTest` runs all 9 single-module cells AND all 6 multi-module cells (15 total). All green. SMOKE_TEST.md §2a no longer needs to be walked by hand at release time.

**Effort.** ~1–1.5 days. Composite-build cells (E, F) are the trickiest because each variant needs two nested Gradle invocations.

**Dependencies.** None — Phase 1 runner is already in place. Independent of items 1, 2.

**Where to start.** `src/smokeTest/java/io/github/fiftieshousewife/smoketest/SmokeVariant.java` (add topology) and `SmokeProject.java` (multi-module scaffolding). Look at the §2a templates in `SMOKE_TEST.md` for the exact layouts.

**Skill.** `.claude/skills/smoke-test` — invoke before designing the multi-module variants.

---

## 4. File the upstream `openrewrite/rewrite-gradle` issue (Gradle 9 catalog regression)

**Goal.** Get the JDK 25 + Gradle 9 catalog blocker fixed upstream so we can re-enable JDK 25 in `integrationTest` (item 5).

**Background.** Bisect performed 2026-05-02 (see `BACKLOG.md` "Re-enable Java 25 in integration tests"):
- `8.81.2` + Gradle **8.14.3**: ✅ both inline and catalog tests pass.
- `8.81.2` + Gradle **9.4.1**: ❌ catalog test fails — `AddDependency` makes no changes to `build.gradle.kts` when `gradle/libs.versions.toml` is present.
- Earlier `openrewrite-core` versions can't run on Gradle 9 at all (`-b` flag rejection; `isGradle9OrLater` gate landed only in 8.81.2).

So 8.81.2 is the first version that runs on Gradle 9, and it has a behaviour difference between Gradle 8 and Gradle 9 in catalog handling specifically. Either the Tooling API in Gradle 9 returns a `GradleProject` model that confuses `AddDependency`'s catalog-detection branch, or `rewrite-gradle`'s build-script editing assumes a Gradle 8 AST shape.

**Steps.**
1. Construct a minimal repro project (no recipe, just `org.openrewrite.gradle.AddDependency` directly):
   - `gradle/libs.versions.toml` empty.
   - `build.gradle.kts` minimal Java + repositories.
   - Invoke `AddDependency` for any `group:artifact:version` and assert that the dependency is NOT added (the bug).
   - Repeat with the toml absent — assert the dependency IS added.
   - Show that switching `withToolingApi("8.14.3")` ↔ `withToolingApi("9.4.1")` flips the result, with no other change.
2. Open the issue at `https://github.com/openrewrite/rewrite/issues` — repo is `openrewrite/rewrite`, the `rewrite-gradle` module is in there.
3. Title: "AddDependency no-op when libs.versions.toml present + Gradle 9.x daemon (works on Gradle 8.x)". Body: link to the bisect notes from `BACKLOG.md`, paste the minimal repro, list versions tested.
4. Link the issue back into `BACKLOG.md` "Re-enable Java 25 in integration tests" so the next session can find it.

**Acceptance.** Issue filed, link recorded in `BACKLOG.md`.

**Effort.** ~30 min.

**Dependencies.** None.

**Where to start.** `BACKLOG.md` "Re-enable Java 25 in integration tests" entry has the bisect data. Construct the minimal repro by adapting `src/integrationTest/java/io/github/fiftieshousewife/recipes/AddLombokDependencyIntegrationTest.java` (the catalog test there is the failure case at the project level — pull out the AddDependency-only essence).

---

## 5. Re-enable JDK 25 in `integrationTest` (downstream of item 4)

**Goal.** Drop the JDK 21 launcher + `release = 21` from the `integrationTest` source set so the integration tests run on the same JDK as everything else.

**Blocked on.** Item 4 — upstream fix for Gradle 9 catalog handling.

**Steps once unblocked.**
1. Bump `openrewrite-core` in `gradle/libs.versions.toml` to whatever version contains the upstream fix.
2. In `build.gradle.kts`, remove the `compileIntegrationTestJava` `release.set(21)` and the `integrationTest` task's `javaLauncher.set(... JavaLanguageVersion.of(21))`.
3. Update each integration test's `withToolingApi()` call: change from no-arg (which defaults to a bundled Gradle 8.x) to `withToolingApi("9.4.1")` so the daemon matches the project's wrapper.
4. `./gradlew integrationTest` — all five test classes should pass.
5. Remove the comment block in `build.gradle.kts` explaining the JDK 21 launcher rationale.
6. Move the `BACKLOG.md` "Re-enable Java 25" entry to Shipped.

**Acceptance.** `integrationTest` runs on JDK 25 + Gradle 9.4.1, all tests green.

**Effort.** ~1 hr once upstream is fixed (test the upgrade, look for any related changes in newer `rewrite-gradle`).

**Dependencies.** Item 4 must be filed AND fixed upstream first.

---

## 6. Expand integration coverage further

**Goal.** Close the remaining bootstrap-only gaps in the `integrationTest` harness.

**Currently covered** (`src/integrationTest/java/io/github/fiftieshousewife/recipes/`):
- `AddLombokDependencyIntegrationTest` — inline + catalog
- `AddSlf4jDependenciesIntegrationTest` — inline (dedup-ordering regression guard) + catalog seeding
- `MigrateToSlf4jRecipeIntegrationTest` — mixed-pattern end-to-end including XML seeding
- `CreateLog4j2ConfigIntegrationTest` — fresh project + `overwriteExisting=false` idempotency
- `ClasspathGateIntegrationTest` — `JavaTransformsClasspathGated` negative case

**Still bootstrap-only.**
- `SystemOutToSlf4jRecipe` — likely covered transitively by `MigrateToSlf4jRecipe`, but a direct test would catch divergence.
- Multi-module / `includeBuild` shapes — the `withToolingApi()` harness only models a single `forProjectDirectory`. The `smokeTest` runner (after Phase 2) covers these end-to-end; if you want them in the in-process tests too, you'd need to figure out whether the tooling API can model multi-module projects.

**Steps for the SystemOutToSlf4jRecipe direct test.**
1. Add `SystemOutToSlf4jRecipeIntegrationTest` mirroring `MigrateToSlf4jRecipeIntegrationTest` but activating only `io.github.fiftieshousewife.SystemOutToSlf4jRecipe` and providing only the `System.out`-using fixture (not the manual-Log4j2 one).
2. Assert post-rewrite Java + the deps + the log4j2.xml + log4j2-test.xml.

**Acceptance.** New test passes; coverage gap closed.

**Effort.** ~30 min for the direct test. Multi-module-via-tooling-api investigation could be hours; defer unless Phase 2 doesn't satisfy the need.

**Dependencies.** None for the direct test. Multi-module-via-tooling-api is downstream of investigating what the tooling API actually exposes.

**Where to start.** Copy `MigrateToSlf4jRecipeIntegrationTest.java` as a template; trim the OrderService fixture; switch the active recipe.

---

## Items intentionally NOT in this plan

- **GitHub Packages publish.** Parked (see `BACKLOG.md` Parked section). Mirror-only; Maven Central is the real channel. Re-open if a consumer specifically asks.
- **Auto version detection at recipe runtime.** Parked. `SMOKE_TEST.md` "Refresh the pinned Lombok and log4j2 versions" is the manual fallback.
- **Log-and-throw cleanup.** Parked. Needs reasoning about whether the throw reaches a boundary that logs.

---

## Suggested order

1. Item 1 (push 0.7) — 5 min, reversible.
2. Item 4 (file upstream issue) — 30 min, unblocks item 5 long-term, no downside.
3. Item 2 (cut 0.7 release) — ~30 min if you're ready to ship; the publish is a one-way door.
4. Item 3 (smoke Phase 2) — biggest chunk, ~1–1.5 days. Replaces the manual gate at item 2 step 3 going forward.
5. Item 6 (expand integration coverage) — ~30 min for the SystemOutToSlf4jRecipe direct test.
6. Item 5 (re-enable JDK 25) — once upstream from item 4 ships.
