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

### G19 update 2026-05-05 — two more variants from `ConcatThrowableMessage`

**G19.2 — multi-line `getDescription` string literal flagged.**

```java
@Override
public String getDescription() {
    return "Rewrites SLF4J log calls of the form `log.error(\"failed: \" + e.getMessage())` "
            + "into `log.error(\"failed: \", e)`. Peels the trailing `+ e.getMessage()` off "
            + "the message and passes the throwable as a separate argument so SLF4J can "
            + "append the stack trace. Multi-part LHS chains (`\"a \" + b + \": \" + "
            + "e.getMessage()`) are preserved verbatim.";
}
```

`ConcatThrowableMessage.java:44`. Heuristic flags the long string literal
as "complex expression should be extracted to a named variable". But
extracting `getDescription`'s body to a `private static final String
DESCRIPTION = "..."` then `return DESCRIPTION;` adds a level of
indirection without explanatory benefit — the method *is* the named
variable, and OpenRewrite's recipe-discovery surface depends on these
strings being directly returned from a getter.

**Suggested fix.** Don't fire G19 on string-only return statements where
the method itself is named like an explanatory accessor (e.g. matches
`get[A-Z].*`). The accessor-name *is* the explanation.

**G19.3 — chained matcher OR is not a candidate for extraction.**

```java
// ConcatThrowableMessageVisitor.java:36
return SLF4J_TRACE.matches(method) || SLF4J_DEBUG.matches(method)
        || SLF4J_INFO.matches(method) || SLF4J_WARN.matches(method)
        || SLF4J_ERROR.matches(method);
```

Five `MethodMatcher.matches(...)` calls joined by `||`. Heuristic flags
"complex expression should be extracted to a named variable". Extracting
to `boolean isLogMethod = ...` would add one line and zero clarity — the
expression already reads as "is this an SLF4J log call". Same family
as G19.1 (lambda after extraction) — heuristic treats syntactic
complexity as proxy for cognitive complexity.

**Suggested fix.** Don't fire G19 on a sequence of identical-shape calls
joined by the same operator. Five `X.matches(y)` calls joined by `||` is
visually structured by repetition and the operator does the explaining.

## G30 Functions Should Do One Thing — first false-positive firing

**G30.1 — guard-clause early returns flagged as multi-purpose method.**

```java
// ConcatThrowableMessageVisitor.java:49
static Optional<J.MethodInvocation> peelThrowableFromConcat(final J.MethodInvocation method) {
    final Expression arg = method.getArguments().get(0);
    if (!(arg instanceof J.Binary binary) || binary.getOperator() != J.Binary.Type.Addition) {
        return Optional.empty();
    }
    if (!(binary.getRight() instanceof J.MethodInvocation getMessageCall)
            || !THROWABLE_GET_MESSAGE.matches(getMessageCall)) {
        return Optional.empty();
    }
    final Expression throwable = getMessageCall.getSelect();
    if (throwable == null) {
        return Optional.empty();
    }
    // ... single transformation ...
}
```

Heuristic counts three `if (!matches) return empty()` guards and
concludes the method does several things. But the method does *one*
thing — peel a Throwable off a concatenation, returning empty when any
shape precondition fails. Guard-clause early returns are exactly what
*Clean Code* Ch3 recommends as an alternative to nested ifs.

This is the first time G30 has fired on this project on idiomatic guard
style — every prior G30 hit (e.g. `SystemOutVisitor.replacePrintln`,
catalogued under "What worked well" below) was a genuine multi-purpose
method that benefited from splitting.

**Suggested fix.** When counting "things this method does", treat
sequential `if (precondition fails) return earlyExit;` blocks as a
single composite precondition, not as separate operations. The signal
to look for is whether the early returns all return the same value (or
shape) — if yes, they're a guard chain, not separate behaviours.

## J3 Constants Versus Enums — overengineering for single-use matchers

**J3.1 — five static-final `MethodMatcher` constants flagged "should be
an enum".**

```java
// ConcatThrowableMessageVisitor.java:18
private static final MethodMatcher SLF4J_TRACE = new MethodMatcher("org.slf4j.Logger trace(..)");
private static final MethodMatcher SLF4J_DEBUG = new MethodMatcher("org.slf4j.Logger debug(..)");
private static final MethodMatcher SLF4J_INFO  = new MethodMatcher("org.slf4j.Logger info(..)");
private static final MethodMatcher SLF4J_WARN  = new MethodMatcher("org.slf4j.Logger warn(..)");
private static final MethodMatcher SLF4J_ERROR = new MethodMatcher("org.slf4j.Logger error(..)");
```

Heuristic spots five fields with a common prefix and suggests an enum.
The enum *would* be valid (the five SLF4J log levels are conceptually
enumerable), but here it would add structure without payoff: the five
matchers are each used exactly once, in a single `||` chain three
lines below. An enum requires `values()`, a constructor, a holder
field, and a lookup method — for a one-call site that's net-negative.

This is the first J3 firing in this project. The rule is sound; the
case here is just at the wrong end of the cost/benefit curve.

**Suggested fix.** Skip J3 when:
1. The constants are all package-private or `private` (not part of an
   API surface that users iterate over), AND
2. Each constant is referenced exactly once in the same compilation
   unit, AND
3. The references are in a single expression (a chain of `||`/`&&`,
   or arguments to one method call).

Together these signal "this is a flat list of literals used once
inline" — exactly the case where the enum overhead doesn't pay back.
For `LoggerNames.java` and `LombokLoggingAnnotation.java` (both real
enums in this project) those preconditions don't hold — multiple usage
sites, lookup methods, dispatch by name — so the rule still fires
correctly there.

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
