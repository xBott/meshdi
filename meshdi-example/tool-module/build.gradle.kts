dependencies {

    implementation(project(":meshdi-api"))
    implementation(project(":meshdi-core"))

    implementation(project(":meshdi-moduleit:api"))
    implementation(project(":meshdi-moduleit:core"))

    annotationProcessor(project(":meshdi-processor"))
    annotationProcessor(project(":meshdi-moduleit:processor"))

    implementation(libs.bundles.kern.default)
    implementation(libs.kern.version)

    implementation(project(":meshdi-example:root-module"))

}

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.get().outputs.files)
    into("/Users/romanplakhotniuk/meshdi_tests/modules")
}