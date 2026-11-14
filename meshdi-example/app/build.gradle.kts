plugins {
    id("com.gradleup.shadow") version "9.2.0"
}

tasks.shadowJar {
    manifest {
        attributes(
            "Main-Class" to "me.bottdev.example.app.App"
        )
    }
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Where the runnable example is assembled. Override with -Pmeshdi.example.dir=/some/path
val exampleRunDir = providers.gradleProperty("meshdi.example.dir")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(rootProject.layout.buildDirectory.dir("example-run"))

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar)
    into(exampleRunDir)
}
