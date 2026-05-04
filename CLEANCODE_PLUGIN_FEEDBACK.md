# Cleancode plugin feedback (v0.1.3)

Notes from the 2026-05-04 cleanup pass on
`io.github.fiftieshousewife:system-out-to-lombok-log4j`. The pass cleared
F2/T9/PMD-AvoidFieldNameMatchingMethodName findings successfully; what
follows are the rules where the heuristic produced false positives or
where the workflow is missing a step.

## G18 Inappropriate Static — false positives

Two FPs reproduce on `SystemOutVisitor.replacePrint` /
`SystemOutVisitor.replacePrintf` even after multiple refactors. Two
distinct heuristic gaps:

**G18.1 — inherited instance methods aren't counted.**
The current rule appears to credit only methods declared in the same
class as evidence of instance binding. Inherited methods like
`getCursor()` (declared on `JavaIsoVisitor`) don't count.

```java
J.MethodInvocation replacePrint(final J.MethodInvocation method, final boolean isError) {
    final List<Expression> args = method.getArguments();
    if (args.size() == 1) {
        return handleSingleArgument(getCursor(), method, args.get(0), isError);  // calls getCursor()
    }
    return method;
}
```

`getCursor()` is unambiguously an instance method, but it lives on the
parent class. Rule still flags `replacePrint`.

**Suggested fix.** Replace
`methodCalls.any { it.declaringClass == currentClass }`
with
`methodCalls.any { !it.isStatic }`.

Cheapest possible fix; would have eliminated all four G18 FPs encountered
in this project.

**G18.2 — method-reference dispatch isn't visible.**

```java
// PrintMethod.java
PRINTLN("println", ..., SystemOutVisitor::replacePrintln),
PRINT  ("print",   ..., SystemOutVisitor::replacePrint),
PRINTF ("printf",  ..., SystemOutVisitor::replacePrintf);

@FunctionalInterface
interface Replacer {
    J.MethodInvocation apply(SystemOutVisitor visitor, J.MethodInvocation method, boolean isError);
}
```

The `Replacer` SAM has the form `(SystemOutVisitor, …) -> …`. Making any
of those three methods static would change the unbound method
reference's effective arity and break the binding. The rule has no way
to know without project-wide reference search.

**Suggested fix (cheap fallback).** Restrict G18 to `private` candidates
only. Method references rarely target `private` (visibility forbids it
across most call sites). Public/package-private methods are exactly the
ones that can be `Class::method`-bound somewhere unexpected.

**Suggested fix (rigorous).** For each candidate `C.m`, scan for
`MemberSelectExpr` of form `T::m` where the lambda's target SAM type is
compatible with `C`'s instance signature. Combine with G18.1 to bound
the cost.

**Recommended ship.** G18.1 + the `private`-only filter together as
v0.1.4. The rigorous variant is a v0.2.x milestone.

## G19 Use Explanatory Variables — over-eager after extraction

After extracting a lambda predicate into a named, package-private,
unit-tested helper, G19 still fires on the line where the helper is
used:

```java
// JulToSlf4jVisitor:39 — current G19 hit
private static J.CompilationUnit withoutJulLoggerImport(final J.CompilationUnit cu) {
    return cu.withImports(cu.getImports().stream()
            .filter(imp -> !isJulLoggerFqn(imp.getTypeName()))
            .toList());
}

static boolean isJulLoggerFqn(final @Nullable String typeName) {  // tested in JulToSlf4jVisitorMethodTest
    return JUL_LOGGER_FQN.equals(typeName);
}
```

The remaining lambda body is `!isJulLoggerFqn(imp.getTypeName())`
— one method call, one negation, one field reference. There's no
expression complex enough to merit further extraction. The heuristic
appears to be triggering on the negation alone.

**Suggested fix.** Don't fire G19 on a lambda whose body is shaped like
`!simpleCall(...)` or `!field.equals(...)` — single-token negation of an
already-named call site is the *result* of an explanatory-variable
refactor, not a candidate for further extraction. Track whether the
called method has a "shape" indicator (named accessor, unit test
covering it, package-private visibility) — if any of those hold, treat
the call as already-explained.

A cheaper proxy: don't fire G19 on lambdas whose body is a single method
call (negated or not). The risk of missing a real G19 here is low —
single-call lambdas almost never benefit from further extraction.

## G5 Duplication — sensitivity to framework boilerplate

Four findings firing on the OpenRewrite recipe-as-value-object idiom:

```java
@Option(displayName = "Require Lombok on classpath",
        description = "When true, only ...",
        required = false)
boolean requireLombokOnClasspath;
```

This block appears on four leaf recipes (each with a slightly different
description string). It can't be deduplicated cleanly because:

- `@Option`-annotated fields must live on the leaf class for OpenRewrite's
  reflection-based option discovery to find them.
- Leaf recipes use `@Value` + `@EqualsAndHashCode(callSuper = false)`,
  which doesn't compose well with abstract base classes.
- The `description` differs per recipe (each one describes what *that*
  recipe is gated on).

The duplication is real but architectural — the framework requires it.

**Suggested fix.** Allow per-finding suppression with a project-scoped
config (e.g. `cleanCode { suppress(G5, file = "AddLombokSlf4jAnnotation.java") }`).
The "Suppress" button in the localhost:7070 report is already exposed in
the UI; persisting those suppressions to a checkable config file would
close the loop.

A complementary fix: make the duplication threshold tuneable (the
"Tune" button in the UI). At least one rule (G30) already exposes this;
G5 should as well.

## `cleanCodeExplain` — missing skill content for some codes

```
$ ./gradlew cleanCodeExplain --finding=G18
> Task :cleanCodeExplain
No skill file found for: G18
```

G18 is one of the rules covered by the `clean-code-classes` skill (per
the skill description in this project's `.claude/skills`). Either the
skill file isn't being looked up by code, or the lookup is incomplete.
Same may apply to other heuristic codes — worth a sweep.

## Suppression workflow — discoverability gap

The `localhost:7070` report has per-finding "Suppress" / "Disable" /
"Tune" buttons. The on-disk persistence format isn't documented (or I
couldn't find it):

- `./gradlew tasks --all` lists `cleanCodeBaseline` ("Snapshot current
  findings as baseline") — close but not the same operation as
  per-finding suppression.
- No `cleanCode { suppress(...) }` DSL that I could discover in
  build.gradle.kts.
- No `.cleancode.yml` / `.cleancode/suppressions.yml` precedent in the
  project tree.

**Suggested addition.** A short README section explaining the round-trip:
which file the UI writes to, what it looks like, and how a CI run picks
it up. A `cleanCodeExplain --finding=<id> --suppression-syntax` mode
that prints the YAML/DSL stanza needed would close the loop.

## What worked well

For balance: the rules that did fire correctly cleared cleanly with
small, code-quality-positive changes. F2 (mutation in
`StringConcatDecomposer`), T9 (test runtime), G30 (function-does-one-thing
in `SystemOutVisitor.replacePrintln`), and the PMD
`AvoidFieldNameMatchingMethodName` rule were all genuine signals worth
acting on. The fix in each case made the code unambiguously better.

The plugin's headline value isn't in dispute — it's the long tail of
heuristic FPs and the suppression discoverability gap that erode trust
in the report.
