dependencies {

    annotationProcessor(project(":meshdi-processor"))
    annotationProcessor(project(":meshdi-moduleit:processor"))

    compileOnly(project(":meshdi-example:root-module"))
    compileOnly("com.squareup.okhttp3:okhttp:5.0.0-alpha.12")

}

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().outputs.files)
    into("/Users/romanplakhotniuk/meshdi_tests/modules")
}