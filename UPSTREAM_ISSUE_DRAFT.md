# Upstream issue draft

## `PrintStackTraceToLogError` precondition does not detect `@Slf4j` inserted mid-pipeline

**Repository:** [openrewrite/rewrite-logging-frameworks](https://github.com/openrewrite/rewrite-logging-frameworks)
**Affected recipe:** `org.openrewrite.java.logging.PrintStackTraceToLogError`
**Reproduced against:** `rewrite-logging-frameworks` resolved via `rewrite-recipe-bom 3.28.0`, OpenRewrite core 8.79.0, JDK 21.

### Summary

When `PrintStackTraceToLogError` runs in a YAML-composed pipeline that adds `@Slf4j` (or any `@lombok.extern.*` annotation) to the source in an *earlier* step of the same pipeline, the recipe's precondition fails to match the freshly-inserted annotation and the `printStackTrace()` rewrite silently no-ops. Standalone runs against source that already carries `@Slf4j` work as expected.

### Root cause hypothesis

`PrintStackTraceToLogError.getVisitor()` wraps its visitor in:

```java
Preconditions.check(
    Preconditions.or(
        new UsesType<>(framework.getLoggerType(), null),
        new UsesType<>("lombok.extern..*", null)),
    visitor)
```

`UsesType` walks resolved types in the source tree. When an earlier recipe inserts `@Slf4j` via `JavaTemplate`, the inserted annotation node carries no resolved `JavaType` for `lombok.extern.slf4j.Slf4j`, even though the import statement is present and the type is on the parser's classpath. `UsesType<lombok.extern..*>` therefore evaluates `false` on that source, the precondition short-circuits, and the visitor never runs.

A second cycle does not help: the freshly-inserted annotation still doesn't carry the resolved type info, and `expectedCyclesThatMakeChanges(2)` rejects the run with "took 1 cycle".

### Reproducer

A composed YAML pipeline that adds `@Slf4j` and then runs `PrintStackTraceToLogError` in the same `rewriteRun`:

```yaml
type: specs.openrewrite.org/v1beta/recipe
name: com.example.AddSlf4jAndConvertPrintStack
recipeList:
  - com.example.AddLombokSlf4jAnnotation   # adds @Slf4j via JavaTemplate
  - org.openrewrite.java.logging.PrintStackTraceToLogError:
      addLogger: false
      loggerName: log
      loggingFramework: SLF4J
```

Input:

```java
package com.example;

public class Mixed {
    public void boom(Exception e) {
        e.printStackTrace();
    }
}
```

Observed output (after the composed recipe runs to a fixed point):

```java
package com.example;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Mixed {
    public void boom(Exception e) {
        e.printStackTrace();   // <-- not rewritten
    }
}
```

Expected output:

```java
package com.example;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Mixed {
    public void boom(Exception e) {
        log.error("Exception", e);
    }
}
```

The intermediate `@Slf4j` IS visible in the actual output — proving the annotation-adding recipe ran successfully — but `PrintStackTraceToLogError` saw the source before its precondition could re-evaluate against the new tree shape.

Verified against a real `GradleProject` marker (Tooling-API integration test against Maven Central), so this is not a stub-classpath artifact.

### Suggested fixes (in order of preference)

1. **Detect Lombok annotations by simple-name walk, not type lookup.** Add a precondition that walks `J.ClassDeclaration.getLeadingAnnotations()` and matches by the annotation expression's printed form (e.g. starts with `Slf4j`, `Log4j2`, `CommonsLog`, etc.). This works regardless of whether the annotation node carries resolved type info.
2. **Re-attribute types between cycles.** Have the runner re-parse compilation units that received `JavaTemplate`-driven insertions before starting the next cycle so `UsesType`-style preconditions see the new types. Heavier change but fixes a whole class of mid-pipeline-insertion problems beyond this one recipe.
3. **Document the constraint.** At minimum, the recipe javadoc should call out that callers must run `@Slf4j`-adding recipes in a separate `rewriteRun` before invoking this recipe, and that composing the two in the same pipeline silently no-ops.

### Workaround

Run the annotation-adding recipe in a *separate* `rewriteRun` before invoking `PrintStackTraceToLogError`. After the first run completes and the source is re-parsed, the second run sees the annotation as a typed reference and the precondition matches.

### Why this matters downstream

Any project that wants to compose `PrintStackTraceToLogError` (or `JulToSlf4j`, `CommonsLogging1ToSlf4j1`, or any other `lombok.extern..*`-detecting recipe) with a Lombok-annotation-adding recipe in a single user-facing pipeline will hit this. For projects that ship `@Slf4j` migration as a single user-invokable recipe (the common case), the workaround of "run two recipes in sequence" is a noticeable UX regression.
