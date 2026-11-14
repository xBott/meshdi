dependencies {

    annotationProcessor(project(":meshdi-processor"))
    annotationProcessor(project(":meshdi-moduleit:processor"))

    compileOnly(project(":meshdi-example:root-module"))
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")

}

// Where the runnable example is assembled. Override with -Pmeshdi.example.dir=/some/path
val exampleRunDir = providers.gradleProperty("meshdi.example.dir")
    .map { rootProject.layout.projectDirectory.dir(it) }
    .orElse(rootProject.layout.buildDirectory.dir("example-run"))

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().outputs.files)
    into(exampleRunDir.map { it.dir("modules") })
}
