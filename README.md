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
fifties-recipes = "0.1"

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
    activeRecipe("org.fifties.housewife.SystemOutToLombokLog4jRecipe")
}
```

3. **Run**:
```bash
./gradlew rewriteDryRun  # Preview
./gradlew rewriteRun     # Apply
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
