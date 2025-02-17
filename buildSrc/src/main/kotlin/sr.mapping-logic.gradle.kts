plugins {
    java
    id("sr.license-logic")
    id("sr.core-dependencies")
    id("io.github.patrick.remapper")
}

dependencies.implementation(project(":mappings:shared"))

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.remap.get().apply {
    archiveClassifier.set("remapped")
}

tasks.named("remap").get().dependsOn("jar")
tasks.named("build").get().dependsOn("remap")

configurations {
    create("remapped") {
        val resultFile = File(File(project.buildDir, "libs"), "${project.name}-${project.version}-remapped.jar")
        val files = project.files(resultFile)
        files.builtBy("remap")

        isCanBeResolved = false
        isCanBeConsumed = true
        outgoing.artifact(resultFile)
        dependencies.add(project.dependencies.create(files))
    }
}
