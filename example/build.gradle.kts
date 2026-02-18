plugins {
    id("java-example-conventions")
    application
    alias(libs.plugins.openrewrite)
}

application {
    mainClass.set("org.example.Main")
}

dependencies {
    rewrite("com.yourorg:recipes:1.0-SNAPSHOT")
}

rewrite {
    activeRecipe("com.yourorg.recipes.AddLombokLog4j2Annotation", "com.yourorg.recipes.SystemOutToLombokLog4j")
}

// Custom task to preview migration changes (dry-run)
tasks.register("migrateDryRun") {
    group = "migration"
    description = "Preview System.out to Lombok @Log4j2 migration changes without applying them"

    dependsOn("rewriteDryRun")

    doLast {
        println("\n=== Migration Preview Complete ===")
        println("Review the changes above to see what would be modified.")
        println("To apply these changes, run: ./gradlew :example:migrate")
    }
}

// Custom task to apply migration changes
tasks.register("migrate") {
    group = "migration"
    description = "Apply System.out to Lombok @Log4j2 migration to source files"

    dependsOn("rewriteRun")

    doLast {
        println("\n=== Migration Applied Successfully ===")
        println("Your source files have been updated to use Lombok @Log4j2 logging.")
        println("Review the changes with: git diff")
    }
}
