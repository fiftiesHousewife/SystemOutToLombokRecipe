# System.out to Lombok @Log4j2

OpenRewrite recipes that migrate Java codebases onto Lombok `@Log4j2` logging. Converts ad-hoc `System.out` / `System.err` / `printStackTrace()` calls, `java.util.logging` (JUL) calls, and hand-rolled Log4j2 `Logger` fields into idiomatic `@Log4j2` + `log.xxx(...)` calls — with parameterized messages and proper stdout/stderr routing.

## What It Does

The default recipe transforms a Java project to:

1. Add Lombok and Log4j2 dependencies to your build (inline, catalog, or not at all — pick a variant below).
2. Create production-ready `src/main/resources/log4j2.xml` (console + rolling file appender, gzip daily rollover, 10 MB size trigger, keeps 10 files) and a console-only `src/test/resources/log4j2-test.xml` that log4j2 auto-selects when tests run.
3. Add `@Log4j2` to classes that use `System.out`/`System.err`, `printStackTrace()`, or `java.util.logging.Logger`.
4. Convert call sites:
   - `System.out.println(...)` → `log.info(...)`
   - `System.err.println(...)` → `log.error(...)`
   - `exception.printStackTrace()` → `log.error("Exception occurred", exception)`
   - String concatenation → parameterized: `"x = " + x` → `log.info("x = {}", x)`
   - `System.out.printf("Name: %s, Age: %d%n", ...)` → `log.info("Name: {}, Age: {}", ...)`
   - `logger.severe/warning/info/config/fine/finer/finest(...)` (JUL) → `log.error/warn/info/debug/debug/trace/trace(...)`

A separate recipe family (`ConvertManualLog4j2ToLombokRecipe*`) migrates projects that already use Log4j2 with hand-rolled `Logger` fields onto `@Log4j2` — see "Migrating existing Log4j2 code" below.

## Prerequisites

- JDK 17 or later (JDK 25 recommended — enables the newest parser)
- Gradle 9.x (wrapper included)

Supports transforming source code written in Java 8 through Java 25.

## Quick Start

```bash
./gradlew build
```

## Using in Your Project

1. **Add versions to `gradle/libs.versions.toml`**:
```toml
[versions]
openrewrite = "7.30.0"
fifties-recipes = "0.4"

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
    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipe")
}
```

3. **Run**:
```bash
./gradlew rewriteDryRun  # Preview
./gradlew rewriteRun     # Apply
```

## Recipes

### `SystemOutToLombokLog4jRecipe` — the one you usually want

Runs all Java transforms, creates the log4j2 configs, and **auto-detects** your dependency setup:

- If your project has a Gradle version catalog (`gradle/libs.versions.toml`), entries are added to it and `build.gradle.kts` uses `libs.lombok`, `libs.log4jApi`, `libs.log4jCore` references.
- Otherwise inline `compileOnly("org.projectlombok:lombok:1.18.44")` etc. declarations are added to `build.gradle.kts`.

You don't pick a variant — the recipe figures it out.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipe")
}
```

### `SystemOutToLombokLog4jRecipeNoDeps`

Runs all the Java transforms and creates the log4j2 configs but touches neither the catalog nor `build.gradle.kts`. Use this in multi-module projects where dependencies live at a parent level, or anywhere you want full manual control.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeNoDeps")
}
```

## Migrating existing Log4j2 code

If your codebase already uses Log4j2 but declares `Logger` fields by hand (`private static final Logger log = LogManager.getLogger(X.class);`), `ConvertManualLog4j2ToLombokRecipe` removes that boilerplate:

- Adds `@Log4j2` to each affected class.
- Deletes the manual field.
- Renames any references to the old field (`logger.info(...)`, `LOG.error(...)`) to `log.xxx(...)`.
- Drops now-unused `org.apache.logging.log4j.Logger` / `LogManager` imports.
- Adds the Lombok dependency (catalog-aware — same auto-detect as above).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.ConvertManualLog4j2ToLombokRecipe")
}
```

A `NoDeps` variant (`ConvertManualLog4j2ToLombokRecipeNoDeps`) exists for projects that manage Lombok separately.

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
import lombok.extern.log4j.Log4j2;

@Log4j2
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
import lombok.extern.log4j.Log4j2;

@Log4j2
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
import lombok.extern.log4j.Log4j2;

@Log4j2
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
import lombok.extern.log4j.Log4j2;

@Log4j2
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
import lombok.extern.log4j.Log4j2;

import java.util.logging.Logger;

@Log4j2
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

The old `Logger logger = Logger.getLogger(...)` field and its `java.util.logging.Logger` import are left in place — remove them yourself once you've confirmed the conversion. (The `ConvertManualLog4j2ToLombokRecipe` family removes hand-rolled Log4j2 fields, but not JUL fields.)

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
import lombok.extern.log4j.Log4j2;

@Log4j2
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

**Don't pollute the code with logging infrastructure.** Every hand-rolled `private static final Logger log = LogManager.getLogger(...);` is a line that isn't about the business problem. It also creates a small opportunity for inconsistency — the wrong class reference, the wrong field name, the wrong logger vendor. `@Log4j2` removes that line entirely: the annotation declares intent, Lombok generates the field, and the class body stays focused on what it's *for*. The `ConvertManualLog4j2ToLombokRecipe*` family in this project exists specifically to strip that boilerplate out of projects that already use Log4j2.

**Use the right tool.** Log4j2 gives you levels, layouts, appenders, filters, asynchronous delivery, and structured output. `System.out.println` gives you a string on a stream. The ratio of capability to line-count is enormous, and picking the right abstraction is — in Martin's framing — a defining habit of professional code.

## Upgrading from 0.3 or 0.4

In 0.5 the `...RecipeCatalog` activeRecipe names are gone. Use the base recipe name — it auto-detects the catalog and does the right thing:

```diff
 rewrite {
-    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeCatalog")
+    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipe")
 }
```

Same change for `ConvertManualLog4j2ToLombokRecipeCatalog` → `ConvertManualLog4j2ToLombokRecipe`. The `NoDeps` variants are unchanged.

## Troubleshooting

**Recipe not found**: Ensure the dependency coordinates and version in your TOML match exactly.

**Build fails after transformation**: Verify dependencies and `log4j2.xml` were added correctly.

**Debug recipes**:
```bash
./gradlew rewriteDiscover          # List available recipes
./gradlew rewriteRun --info        # Verbose output
```

## Resources

- [OpenRewrite Documentation](https://docs.openrewrite.org/)
- [Lombok @Log4j2](https://projectlombok.org/features/log)
- [Apache Log4j2](https://logging.apache.org/log4j/2.x/)

---

**Built with OpenRewrite • Lombok • Log4j2**
