# AI agent session notes

Project-specific guidance for this repo, intended for any AI coding agent working on the codebase (Claude Code, Cursor, Codex, OpenCode, etc.). **Generic patterns live in the skills at `.claude/skills/` — invoke those instead of duplicating their content here.**

## Domain skills (committed)

| Skill | When to invoke |
| --- | --- |
| `new-recipe` | Authoring a new OpenRewrite recipe: visitor structure, `MethodMatcher`, YAML composition, manifest location, marker-preserving tree edits, `@Option` patterns. |
| `recipe-testing` | Writing tests for a recipe: integration vs. unit split, `RewriteTest` / `TypeValidation.none()`, multi-source `rewriteRun`, `GradleProject` marker injection, matrix-test layout. |
| `smoke-test` | Designing or extending the pre-release smoke-test procedure: `/tmp` project bootstrap, dryRun/Run/compile cycle, project-shape matrix, expected-outcomes tables, mavenLocal resolution check. |

If you're asked to do any of those things, invoke the skill — don't re-derive the patterns inline.

## Clean Code skills (installed by `./gradlew installSkills`, not committed)

When the cleancode plugin (`./gradlew analyseCleanCode`, or the live report at `localhost:7070` via `./gradlew cleanCodeServe`) fires a finding, invoke the matching skill before fixing. Each skill captures the *shape* of the right fix for that family, not just "silence the warning."

| Skill | Codes covered |
| --- | --- |
| `clean-code-functions` | F1, F2, F3, G5, G30, G34, Ch3.1–3.3 |
| `clean-code-naming` | N1, N5, N6, N7, G11, G16 |
| `clean-code-classes` | Ch10.1, Ch10.2, G8, G14, G17, G18 |
| `clean-code-conditionals-and-expressions` | G19, G23, G28, G29, G33 |
| `clean-code-java-idioms` | J1, J2, J3, G1, G4, G25, G26 |
| `clean-code-null-handling` | Ch7.2 |
| `clean-code-exception-handling` | Ch7.1 |
| `clean-code-comments-and-clutter` | C3, C5, G9, G10, G12, G24 |
| `clean-code-test-quality` | T1, T3, T4 |
| `clean-code-project-conventions` | project-specific conventions not covered by a single heuristic |

Some findings are false positives — known patterns include G18 inheritance + method-reference blind spots, G19 over-eager firing after extraction, and G5 sensitivity to OpenRewrite framework boilerplate. The skill's first job is to triage real-vs-noise.

## Project structure

```
src/
├── main/
│   ├── java/io/github/fiftieshousewife/cleanlogging/
│   │   ├── *Recipe class files          # leaf recipes (one per transformation)
│   │   └── LombokClasspathGate.java     # shared helpers (package-private)
│   └── resources/META-INF/rewrite/
│       └── clean-logging.yml     # composed top-level recipes
└── test/
    └── java/io/github/fiftieshousewife/cleanlogging/
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
