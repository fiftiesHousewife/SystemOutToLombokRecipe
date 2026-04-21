# Claude Code session notes

Project-specific guidance for this repo. **Generic patterns live in the four skills at `.claude/skills/` — invoke those instead of duplicating their content here.**

| Skill | When to invoke |
| --- | --- |
| `new-gradle-project` | Bootstrapping a fresh Gradle build: TOML version catalog, condensed JUnit, Ben-Manes, `gradle.properties` JVM args, build-file skeleton. |
| `new-recipe` | Authoring a new OpenRewrite recipe: visitor structure, `MethodMatcher`, YAML composition, manifest location, marker-preserving tree edits, `@Option` patterns. |
| `recipe-testing` | Writing tests for a recipe: integration vs. unit split, `RewriteTest` / `TypeValidation.none()`, multi-source `rewriteRun`, `GradleProject` marker injection, matrix-test layout. |
| `smoke-test` | Designing or extending the pre-release smoke-test procedure: `/tmp` project bootstrap, dryRun/Run/compile cycle, project-shape matrix, expected-outcomes tables, mavenLocal resolution check. |

If you're asked to do any of those things, invoke the skill — don't re-derive the patterns inline.

## Project structure

```
src/
├── main/
│   ├── java/io/github/fiftieshousewife/recipes/
│   │   ├── *Recipe class files          # leaf recipes (one per transformation)
│   │   └── LombokClasspathGate.java     # shared helpers (package-private)
│   └── resources/META-INF/rewrite/
│       └── system-out-to-lombok.yml     # composed top-level recipes
└── test/
    └── java/io/github/fiftieshousewife/recipes/
        ├── *Test.java                   # RewriteTest integration tests
        ├── *MethodTest.java             # unit tests for package-private helpers
        └── matrix/                      # KotlinDslMatrixTest + GroovyDslMatrixTest
```

Key files at repo root:

- `build.gradle.kts` + `gradle/libs.versions.toml` — build + version pinning
- `SMOKE_TEST.md` — the pre-release release gate (§2a covers the six project-shape templates)
- `BACKLOG.md` — Shipped / Queued / Active / Parked
- `README.md` — user-facing

## Publication workflow

Before tagging and pushing a new version, run these in order. Skipping a step is how we ship regressions.

1. **Quality gates**: `./gradlew check` — must be green (tests, JaCoCo 90% instruction/method gate, SpotBugs).
2. **Smoke tests**: work through every template in `SMOKE_TEST.md` — §1 (build local jar), §2 (per-variant bootstrap), §2a (six project-shape templates), §3 (mavenLocal resolution). The RewriteTest matrix is an approximation; the /tmp Gradle smoke tests are authoritative. Don't skip this just because `check` passes.
3. **Update `README.md`** if any recipe surface changed — new recipe, new option, new supported project shape. Both the Recipes section and the "Supported project shapes" table.
4. **Update `BACKLOG.md`** — move whatever's shipping out of Active or Queued-for-next-release into Shipped with a new version heading and one-paragraph release notes per item.
5. **Bump `version` in `build.gradle.kts`** — `x.y` → `x.(y+1)` for additive changes, `(x+1).0` for anything source-incompatible.
6. **Commit + push** — one release commit is fine if the per-feature history is already good; otherwise rebase first. `git add` specific files, don't `-A`.
7. **Publish**: `./gradlew publishAndReleaseToMavenCentral`. One-way door — the version becomes immutable. If signing credentials are missing the task fails loudly.
8. **Tag**: `git tag v<version> && git push origin v<version>`.

## Coding standards (not covered by tools)

SpotBugs and the compiler catch most things. These are the rules they don't:

- **No comments in tests.** The method name is the documentation. If you need a comment, the test name is wrong.
- **Helpers are package-private, not private.** Same-package tests call them directly. Only mark `private` when something genuinely must not leave the class.
- **Break complex visitor methods into named helpers.** Each helper should be independently testable with a clear pass/fail. A 30-line `visitMethodInvocation` is a refactor waiting to happen.
- **Explicit `@SuppressWarnings`.** Every warning is fixed or suppressed with a specific category (`"unchecked"`, `"DataFlowIssue"`) — no blanket suppressions, no ignored warnings.
- **No emojis in source, docs, or commits** unless the user explicitly asks.
- **Prefer editing existing files over creating new ones.** Only add a new file when the new responsibility genuinely doesn't belong in an existing one.
- **No abstractions ahead of need.** Three similar lines beats a premature helper. Don't design for hypothetical future requirements — the codebase is small enough that refactoring when the third use-site appears is cheap.
- **No error handling for scenarios that can't happen.** Trust framework guarantees. Validate at real boundaries (user input, external APIs), not between your own functions.
- **README stays concise and user-focused.** If you find yourself documenting *how* a recipe works internally, the content belongs in a code comment at most — not in README.

## Coding standards enforced by tools

Not your job to remember — but know they exist:

- `./gradlew check` → 90% JaCoCo instruction + method coverage (drops fail the build).
- `./gradlew check` → SpotBugs default effort, DEFAULT confidence, `ignoreFailures = false`.
- `compileJava` → `-parameters`, `release = 17` (production code must stay 17-compatible).
- `compileTestJava` → `-parameters`, `release = 25` (tests can use newer language features).
