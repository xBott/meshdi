dependencies {
    annotationProcessor(project(":meshdi-processor"))
    annotationProcessor(project(":meshdi-moduleit:processor"))

}

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().outputs.files)
    into("/Users/romanplakhotniuk/meshdi_tests/modules")
}