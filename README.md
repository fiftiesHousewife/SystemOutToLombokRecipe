# System.out to Lombok @Log4j2

OpenRewrite recipe that automatically converts `System.out.println()`, `System.out.printf()`, and `printStackTrace()` calls to Lombok `@Log4j2` logging with parameterized statements.

## What It Does

Automatically transforms your code to:
1. Add Lombok and Log4j2 dependencies to your project
2. Create a log4j2.xml configuration file
3. Add `@Log4j2` annotation to classes using `System.out` or `printStackTrace()`
4. Convert `System.out.println()` → `log.info()`
5. Convert `System.err.println()` → `log.error()`
6. Convert `exception.printStackTrace()` → `log.error("Exception occurred", exception)`
7. Convert string concatenation to parameterized logging: `"x = " + x` → `"x = {}", x`
8. Convert `System.out.printf()` format strings to parameterized logging: `"Name: %s, Age: %d%n"` → `"Name: {}, Age: {}"`

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
fifties-recipes = "0.3"

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

## Version Catalog Projects

If your project uses a Gradle version catalog (`gradle/libs.versions.toml`), there are two purpose-built variants. Pick one:

### `SystemOutToLombokLog4jRecipeCatalog` (recommended for catalogs)

Auto-populates your version catalog with Lombok and Log4j2 entries, runs the Java transforms, and creates `log4j2.xml`. After running, you add four lines to your `build.gradle.kts` dependencies block (automating this is on the 0.4 roadmap).

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeCatalog")
}
```

After running, your `libs.versions.toml` will contain:

```toml
[versions]
lombok = "1.18.44"
log4jApi = "2.25.4"
log4jCore = "2.25.4"

[libraries]
lombok = { module = "org.projectlombok:lombok", version.ref = "lombok" }
log4jApi = { module = "org.apache.logging.log4j:log4j-api", version.ref = "log4jApi" }
log4jCore = { module = "org.apache.logging.log4j:log4j-core", version.ref = "log4jCore" }
```

Add to your `build.gradle.kts` dependencies block:

```kotlin
dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    implementation(libs.log4jApi)
    runtimeOnly(libs.log4jCore)
}
```

### `SystemOutToLombokLog4jRecipeNoDeps`

For cases where you want full manual control — runs all Java transforms and creates `log4j2.xml` but touches neither the catalog nor `build.gradle.kts`. You add everything yourself.

```kotlin
rewrite {
    activeRecipe("io.github.fiftieshousewife.SystemOutToLombokLog4jRecipeNoDeps")
}
```

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

**Don't pollute the code with logging infrastructure.** Every hand-rolled `private static final Logger log = LogManager.getLogger(...);` is a line that isn't about the business problem. It also creates a small opportunity for inconsistency — the wrong class reference, the wrong field name, the wrong logger vendor. `@Log4j2` removes that line entirely: the annotation declares intent, Lombok generates the field, and the class body stays focused on what it's *for*. The `ConvertManualLog4j2ToLombokRecipeNoDeps` recipe in this project exists specifically to strip that boilerplate out of projects that already use Log4j2.

**Use the right tool.** Log4j2 gives you levels, layouts, appenders, filters, asynchronous delivery, and structured output. `System.out.println` gives you a string on a stream. The ratio of capability to line-count is enormous, and picking the right abstraction is — in Martin's framing — a defining habit of professional code.

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
