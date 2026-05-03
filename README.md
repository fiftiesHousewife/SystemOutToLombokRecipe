# System.out to Lombok @Slf4j

OpenRewrite recipes that migrate Java codebases onto Lombok `@Slf4j` + SLF4J logging (with Log4j2 as the backend). Converts ad-hoc `System.out` / `System.err` / `printStackTrace()` calls, `java.util.logging` (JUL) calls, and hand-rolled Log4j2 `Logger` fields into idiomatic `@Slf4j` + `log.xxx(...)` calls — with parameterized messages and proper stdout/stderr routing.

Your application code talks to SLF4J (the standard Java logging facade), so it's never coupled to a specific backend. Log4j2 handles the actual log routing and file rolling under the covers via the `log4j-slf4j2-impl` bridge.

## What It Does

The default recipe, `SystemOutToSlf4jRecipe`, takes a project that's using some mix of `System.out`/`System.err`, `printStackTrace()`, and/or `java.util.logging` and moves it onto Lombok `@Slf4j` logging. On any given class it will:

1. **Add the right dependencies to your build.**
   Lombok (`compileOnly` + `annotationProcessor`), SLF4J (`slf4j-api`), the SLF4J-to-Log4j2 bridge (`log4j-slf4j2-impl`), and the Log4j2 backend (`log4j-core`). The recipe auto-detects whether the project uses a Gradle version catalog: if `gradle/libs.versions.toml` exists, entries land in the catalog and your `build.gradle.kts` gets `libs.lombok`/`libs.slf4jApi`/`libs.log4jSlf4jImpl`/`libs.log4jCore`; otherwise inline `"group:artifact:version"` declarations are added.

2. **Create the Log4j2 configs.**
   `src/main/resources/log4j2.xml` — production-ready: non-error levels to stdout, errors to stderr (respecting the `System.out` vs `System.err` split), plus a rolling file appender under `./logs/` (daily + 10 MB gzip rollover, keeps 10 files).
   `src/test/resources/log4j2-test.xml` — console only, so unit tests don't spam `./logs/`.

3. **Add `@Slf4j`** to every class that needs a logger — whether it has `System.out`/`System.err` calls, `printStackTrace()` calls, or a `java.util.logging.Logger` field.

4. **Rewrite the call sites:**

   **`System.out` / `System.err`** — stops the "poor man's logging" anti-pattern of writing straight to the console:
   - `System.out.println(...)` → `log.info(...)`
   - `System.err.println(...)` → `log.error(...)`
   - `System.out.printf("Name: %s, Age: %d%n", ...)` → `log.info("Name: {}, Age: {}", ...)` — printf specifiers become parameterized `{}` placeholders so the log framework can do structured formatting (and skip it when the level is disabled).
   - `System.out.println("x = " + x)` → `log.info("x = {}", x)` — string-concatenated messages are decomposed into a format string plus args, so the logger isn't paying for string-building on disabled levels.

   **`Throwable.printStackTrace()`** — the other popular "logging by accident" pattern. Replaced with `log.error("Exception occurred", exception)` so the trace goes through the configured appenders, with the exception properly attached rather than dumped to stderr.

   **`java.util.logging.Logger` (JUL)** — the JDK's built-in logging framework is awkward (static `Level` values, no parameterized messages, minimal out-of-the-box config). Migrating to Log4j2 gives you the same features as the rest of the code base. The recipe maps JUL levels to the closest Log4j2 equivalent:
   - `severe` → `error`
   - `warning` → `warn`
   - `info` → `info`
   - `config` / `fine` → `debug`
   - `finer` / `finest` → `trace`

   After the call conversion, the hand-rolled `Logger logger = Logger.getLogger(...)` field and the `java.util.logging.Logger` import are removed if nothing else in the class still references them.

A companion recipe, `ConvertManualLoggerToSlf4jRecipe`, handles the separate case of codebases that are **already on Log4j2** but declare their `Logger` fields by hand (`private static final Logger log = LogManager.getLogger(X.class);`). It replaces that boilerplate with `@Slf4j`, renames references (`logger`/`LOG`/`LOGGER` → `log`), and cleans up the now-unused `org.apache.logging.log4j.Logger`/`LogManager` imports. See "Migrating existing Log4j2 code" below.

## Prerequisites

- JDK 17 or later (JDK 25 recommended — enables the newest parser)
- Gradle 9.x (wrapper included)

Supports transforming source code written in Java 8 through Java 25.

## Supported project shapes

Exercised by the matrix tests under `src/test/java/io/github/fiftieshousewife/recipes/matrix/` and pre-release smoke-tested against real Gradle projects (`SMOKE_TEST.md` §2a).

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
fifties-recipes = "0.7"

[libraries]
fifties-systemout = { module = "io.github.fiftieshousewife:system-out-to-lombok-log4j", version.ref = "fifties-recipes" }

[plugins]
openrewrite = { id = "org.openrewrite.rewrite", version.ref = "openrewrite" }
```

2. **Add to your `build.gradle.kts`**:
```kotlin
plugins {
    alias(libs.plugins.openrewrite)
}

dependencies {
    rewrite(libs.fifties.systemout)
}

rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToSlf4jRecipe")
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

### `SystemOutToSlf4jRecipe` — the one you usually want

Runs all Java transforms, creates the log4j2 configs, and **auto-detects** your dependency setup:

- If your project has a Gradle version catalog (`gradle/libs.versions.toml`), entries are added to it and `build.gradle.kts` uses `libs.lombok`, `libs.log4jApi`, `libs.log4jCore` references.
- Otherwise inline `compileOnly("org.projectlombok:lombok:1.18.44")` etc. declarations are added to `build.gradle.kts`.

You don't pick a variant — the recipe figures it out.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToSlf4jRecipe")
}
```

### `SystemOutToSlf4jRecipeNoDeps`

Runs all the Java transforms and creates the log4j2 configs but touches neither the catalog nor `build.gradle.kts`. Use this in multi-module projects where dependencies live at a parent level, or anywhere you want full manual control.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToSlf4jRecipeNoDeps")
}
```

### `MigrateToSlf4jRecipe` — everything at once

Does what `SystemOutToSlf4jRecipe` does **plus** the hand-rolled Log4j2 conversion below, in a single pass. Use this when your codebase has a mix of several of these patterns (a `System.out.println` here, a `java.util.logging.Logger` there, a `private static final Logger log = LogManager.getLogger(…)` over there) so you don't have to run two recipes in sequence.

The focused recipes (`SystemOutToSlf4jRecipe`, `ConvertManualLoggerToSlf4jRecipe`) still exist and run faster — use them if you only have one pattern to fix.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.MigrateToSlf4jRecipe")
}
```

A `NoDeps` variant (`MigrateToSlf4jRecipeNoDeps`) exists for projects that manage dependencies at a parent level.

## Migrating existing Log4j2 code

If your codebase already uses Log4j2 but declares `Logger` fields by hand (`private static final Logger log = LogManager.getLogger(X.class);`), `ConvertManualLoggerToSlf4jRecipe` removes that boilerplate:

- Adds `@Slf4j` to each affected class.
- Deletes the manual field.
- Renames any references to the old field (`logger.info(...)`, `LOG.error(...)`) to `log.xxx(...)`.
- Drops now-unused `org.apache.logging.log4j.Logger` / `LogManager` imports.
- Adds the deps the rewritten code needs — Lombok (compileOnly + annotationProcessor), `slf4j-api` (for the `org.slf4j.Logger` field that `@Slf4j` generates), and the `log4j-slf4j2-impl` bridge so SLF4J calls still route through Log4j2 at runtime. Catalog-aware (same auto-detect as above).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.ConvertManualLoggerToSlf4jRecipe")
}
```

A `NoDeps` variant (`ConvertManualLoggerToSlf4jRecipeNoDeps`) exists for projects that manage Lombok separately. If you have both this *and* `System.out` / JUL patterns to migrate, see `MigrateToSlf4jRecipe` above.

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
import java.util.logging.Logger;

public class Service {
    private static final Logger logger = Logger.getLogger(Service.class.getName());

    public void run() {
        logger.info("starting");
        logger.severe("boom");
        logger.fine("verbose");
    }
}
```

**After**:
```java
import lombok.extern.slf4j.Slf4j;

import java.util.logging.Logger;

@Slf4j
public class Service {
    private static final Logger logger = Logger.getLogger(Service.class.getName());

    public void run() {
        log.info("starting");
        log.error("boom");
        log.debug("verbose");
    }
}
```

Level mapping: `severe` → `error`, `warning` → `warn`, `info` → `info`, `config`/`fine` → `debug`, `finer`/`finest` → `trace`.

The old `Logger logger = Logger.getLogger(...)` field and its `java.util.logging.Logger` import are left in place — remove them yourself once you've confirmed the conversion. (The `ConvertManualLoggerToSlf4jRecipe` family removes hand-rolled Log4j2 fields, but not JUL fields.)

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

## For maintainers (and any agent working in this repo)

**IMPORTANT**: this repo ships four Claude Code skills under `.claude/skills/`. They capture the patterns we've converged on and should be invoked rather than re-derived in any future development work. They are:

- **`.claude/skills/new-gradle-project/`** — **IMPORTANT** when bootstrapping a new Gradle build. Covers TOML version catalog layout, condensed JUnit dependency block, Ben-Manes versions plugin wiring, `gradle.properties` JVM args that silence Java 21+ native-access warnings, and a current `build.gradle.kts` skeleton with split `release` targets.
- **`.claude/skills/new-recipe/`** — **IMPORTANT** when authoring a new OpenRewrite recipe. Covers the `moderneinc/rewrite-recipe-starter` template, the correct `src/main/resources/META-INF/rewrite/` manifest location, visitor structure with package-private helpers, `MethodMatcher` usage, marker-preserving argument-level tree edits (the pattern that keeps Groovy DSL paren-less), YAML composition, and `@Option(required = false)` patterns.
- **`.claude/skills/recipe-testing/`** — **IMPORTANT** when writing or restructuring tests. Covers the two-layer integration + unit test split, `RewriteTest` / `TypeValidation.none()` for Lombok-aware recipes, multi-source `rewriteRun` with `java()` + `buildGradle[Kts]()` + `toml()` + `properties()`, `GradleProject` marker injection for multi-module simulation, and the project-shape matrix-test pattern.
- **`.claude/skills/smoke-test/`** — **IMPORTANT** when designing or extending the pre-release smoke-test procedure. Covers the `/tmp` throwaway-project bootstrap, the `rewriteDryRun` → inspect → `rewriteRun` → `compileJava` cycle, the `include` vs. `includeBuild` distinction, the expected-outcomes table format, and the resolve-by-mavenLocal-coordinates check.

`CLAUDE.md` is the entry point for anything that's specific to this particular repo (project structure, the publication workflow, the coding standards SpotBugs doesn't catch). If you're tempted to add generic recipe / testing / Gradle / smoke-test guidance to `CLAUDE.md`, stop — that content belongs in the corresponding skill.

---

**Built with OpenRewrite • Lombok • Log4j2**
