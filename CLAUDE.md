# Claude Code Session Notes

This document captures the best practices and patterns established during the development of this OpenRewrite recipe project.

## Project Setup Best Practices

### 1. Use Version Catalogs (TOML)

**Always centralize dependency versions in `gradle/libs.versions.toml`:**

```toml
[versions]
openrewrite = "7.26.0"
junit = "6.1.0-M1"

[libraries]
openrewrite-java = { module = "org.openrewrite:rewrite-java" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter", version.ref = "junit" }

[plugins]
openrewrite = { id = "org.openrewrite.rewrite", version.ref = "openrewrite" }
```

**Benefits:**
- Single source of truth for all versions
- No hardcoded versions in build files
- Easy to update dependencies
- Type-safe accessors in IDE

**Usage in build.gradle.kts:**
```kotlin
dependencies {
    testImplementation(libs.junit.jupiter)
    rewrite(platform(libs.openrewrite.recipe.bom))
}
```

### 2. Condense JUnit Dependencies

**Before (5 lines):**
```kotlin
testImplementation(platform("org.junit:junit-bom:6.0.3"))
testImplementation(libs.junit.jupiter.api)
testImplementation(libs.junit.jupiter.params)
testRuntimeOnly(libs.junit.jupiter.engine)
testRuntimeOnly(libs.junit.platform.launcher)
```

**After (2 lines):**
```kotlin
testImplementation(libs.junit.jupiter)  // Aggregates api, params, engine
testRuntimeOnly(libs.junit.platform.launcher)
```

### 3. Add Ben-Manes Versions Plugin

Essential for dependency management:

```kotlin
plugins {
    alias(libs.plugins.versions)
}
```

**Usage:**
```bash
./gradlew dependencyUpdates  # Shows available updates
```

### 4. Recipe File Location

**Correct:** `src/main/resources/META-INF/rewrite/your-recipe.yml`

This is where OpenRewrite automatically discovers recipes. Do NOT put recipe YAML files in the project root.

### 5. Fix Java 21 Native Access Warning

Add to `gradle.properties`:
```properties
org.gradle.jvmargs=--add-opens=java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED
```

This minimizes warnings from Gradle's native library on Java 21+.

## Code Quality Best Practices

### 1. No Comments in Tests

**Bad:**
```java
@Test
void testSomething() {
    // Create a user
    User user = new User();
    // Assert it's valid
    assertThat(user.isValid()).isTrue();
}
```

**Good:**
```java
@Test
void testSomething() {
    User user = new User();
    assertThat(user.isValid()).isTrue();
}
```

Test method names should be self-documenting. Comments are unnecessary noise.

### 2. Package-Private Methods for Testing

Make helper methods package-private (no modifier) instead of private to enable unit testing:

**Bad:**
```java
public class MyRecipe extends Recipe {
    private String buildTemplate() {  // Can't test
        return "template";
    }
}
```

**Good:**
```java
public class MyRecipe extends Recipe {
    String buildTemplate() {  // Can test from same package
        return "template";
    }
}
```

Then write dedicated unit tests:
```java
class MyRecipeMethodTest {
    @Test
    void buildTemplate_returnsCorrectFormat() {
        MyRecipe recipe = new MyRecipe();
        // Test the package-private method directly
    }
}
```

### 3. Break Down Complex Logic

**Bad - One large method:**
```java
@Override
public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
    // 100 lines of complex logic
}
```

**Good - Small, testable methods:**
```java
@Override
public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
    if (!isSystemOut(method)) return method;
    return convertToLog(method, getLogLevel(method));
}

boolean isSystemOut(J.MethodInvocation method) { /* ... */ }
String getLogLevel(J.MethodInvocation method) { /* ... */ }
J.MethodInvocation convertToLog(J.MethodInvocation method, String level) { /* ... */ }
```

Each method is now individually testable.

### 4. Fix Compiler Warnings Elegantly

**Unchecked cast warnings:**
```java
@Test
@SuppressWarnings("unchecked")
void testMethod() {
    JavaIsoVisitor<ExecutionContext> visitor =
        (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();
}
```

**Null argument warnings in tests:**
```java
@Test
@SuppressWarnings({"unchecked", "DataFlowIssue"})
void testMethod() {
    parts.add(new J.Literal(null, null, null, "text", "\"text\"", null, null));
}
```

Only suppress where necessary and be explicit about what you're suppressing.

## Project Structure

### Recommended Recipe Organization

```
src/
├── main/
│   ├── java/com/yourorg/recipes/
│   │   ├── AddLombokLog4j2Annotation.java
│   │   ├── SystemOutToLombokLog4j.java
│   │   └── PrintStackTraceToLog.java
│   └── resources/META-INF/rewrite/
│       └── system-out-to-lombok.yml
└── test/
    └── java/com/yourorg/recipes/
        ├── AddLombokLog4j2AnnotationTest.java
        ├── AddLombokLog4j2AnnotationMethodTest.java  # Unit tests
        ├── SystemOutToLombokLog4jTest.java
        ├── SystemOutToLombokLog4jMethodTest.java     # Unit tests
        ├── PrintStackTraceToLogTest.java
        └── AddLombokLog4j2AnnotationPrintStackTraceTest.java
```

### Testing Strategy

1. **Integration tests** (`*Test.java`): Test the full recipe with before/after code
2. **Unit tests** (`*MethodTest.java`): Test individual package-private helper methods

This provides comprehensive coverage and helps isolate bugs.

## Starting a New Recipe Project

### Use the Official Template

Start from: https://github.com/moderneinc/rewrite-recipe-starter

Then apply these modifications:
1. Add version catalog (`gradle/libs.versions.toml`)
2. Move all versions to TOML
3. Add Ben-Manes versions plugin
4. Condense JUnit dependencies
5. Put recipes in correct location: `src/main/resources/META-INF/rewrite/`
6. Add JVM args to `gradle.properties`
7. Break down complex methods
8. Write unit tests for helper methods
9. Remove unnecessary comments
10. Fix all compiler warnings

## Gradle Configuration

### Complete gradle.properties

```properties
org.gradle.jvmargs=--add-opens=java.base/java.lang=ALL-UNNAMED --enable-native-access=ALL-UNNAMED
```

### Key Build File Practices

```kotlin
plugins {
    java
    application
    alias(libs.plugins.openrewrite)
    alias(libs.plugins.versions)
    `maven-publish`
}

dependencies {
    // Use platform BOM for version management
    implementation(platform("org.openrewrite.recipe:rewrite-recipe-bom:latest.release"))

    // Reference from version catalog
    implementation(libs.openrewrite.java)
    testImplementation(libs.junit.jupiter)

    // Self-reference for testing
    rewrite(platform(libs.openrewrite.recipe.bom))
    rewrite(libs.openrewrite.gradle)
    rewrite(project)  // Important: test your own recipes
}

// Compile recipes at Java 17 — OpenRewrite 8.x requires JDK 17+ at runtime,
// so targeting 8 doesn't buy real compatibility and just generates obsolete-target warnings.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(17)
}

// Compile tests at the toolchain version so tests can use the newest language features.
tasks.named<JavaCompile>("compileTestJava") {
    options.compilerArgs.add("-parameters")
    options.release.set(25)
}
```

## Recipe Development Patterns

### 1. Visitor Pattern Structure

```java
@Override
public TreeVisitor<?, ExecutionContext> getVisitor() {
    return new JavaIsoVisitor<ExecutionContext>() {
        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);

            if (!matchesCriteria(mi)) {
                return mi;
            }
put back the comp
            return transform(mi);
        }

        // Package-private helper methods here
        boolean matchesCriteria(J.MethodInvocation method) { /* ... */ }
        J.MethodInvocation transform(J.MethodInvocation method) { /* ... */ }
    };
}
```

### 2. Use MethodMatcher for Type Safety

```java
private static final MethodMatcher PRINT_STACK_TRACE =
    new MethodMatcher("java.lang.Throwable printStackTrace(..)");

if (PRINT_STACK_TRACE.matches(method)) {
    // Handle it
}
```

### 3. Recipe Composition in YAML

```yaml
---
type: specs.openrewrite.org/v1beta/recipe
name: com.yourorg.MyRecipe
displayName: My Recipe
description: Does something useful

recipeList:
  - com.yourorg.recipes.Step1
  - com.yourorg.recipes.Step2
  - com.yourorg.recipes.Step3
```

Keep individual recipes focused. Compose them in YAML.

## Testing Best Practices

### 1. Use RewriteTest Interface

```java
class MyRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new MyRecipe())
            .parser(JavaParser.fromJavaVersion())
            .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void convertsSimpleCase() {
        rewriteRun(
            java(
                """
                // before
                """,
                """
                // after
                """
            )
        );
    }
}
```

### 2. Test Individual Methods

```java
class MyRecipeMethodTest {
    @Test
    @SuppressWarnings("unchecked")
    void helperMethod_returnsExpectedValue() {
        MyRecipe recipe = new MyRecipe();
        JavaIsoVisitor<ExecutionContext> visitor =
            (JavaIsoVisitor<ExecutionContext>) recipe.getVisitor();

        // Test package-private method via reflection or direct access
        String result = invokeHelperMethod(visitor, "input");
        assertThat(result).isEqualTo("expected");
    }
}
```

### 3. TypeValidation.none() for Lombok

When testing recipes that use Lombok annotations, use `TypeValidation.none()` since Lombok generates code at compile time that OpenRewrite's test parser can't resolve. This is documented in OpenRewrite FAQ and is the correct approach.

## Documentation

Keep README concise:
- What it does (brief list)
- How to use it (quick start)
- Examples (before/after code)
- Troubleshooting (common issues only)

**Remove:**
- Internal implementation details
- Architecture explanations
- Design patterns used
- How it works internally

Users don't need to know HOW it works, just WHAT it does and HOW to use it.

## Summary Checklist

When creating or reviewing an OpenRewrite recipe project:

- [ ] All versions in `gradle/libs.versions.toml`
- [ ] No hardcoded versions in `build.gradle.kts`
- [ ] Ben-Manes versions plugin added
- [ ] JUnit dependencies condensed (2 lines max)
- [ ] Recipes in `src/main/resources/META-INF/rewrite/`
- [ ] No duplicate recipe YAML files
- [ ] JVM args in `gradle.properties`
- [ ] Helper methods are package-private
- [ ] Unit tests for all helper methods
- [ ] Integration tests for full recipes
- [ ] No comments in test files
- [ ] All compiler warnings fixed with `@SuppressWarnings`
- [ ] README is concise and user-focused
- [ ] All tests pass
- [ ] `./gradlew dependencyUpdates` shows latest versions

Following these patterns results in clean, maintainable, well-tested OpenRewrite recipes.
