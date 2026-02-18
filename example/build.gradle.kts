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
