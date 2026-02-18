plugins {
    java
    application
    id("org.openrewrite.rewrite")
}

application {
    mainClass.set("org.example.Main")
}

repositories {
    mavenLocal()
}

dependencies {
    // OpenRewrite recipe dependency from local Maven
    rewrite("com.yourorg:recipes:1.0-SNAPSHOT")
}

rewrite {
    // Use the composite recipe that includes everything
    activeRecipe("com.yourorg.recipes.AddLombokLog4j2Annotation", "com.yourorg.recipes.SystemOutToLombokLog4j")
}
