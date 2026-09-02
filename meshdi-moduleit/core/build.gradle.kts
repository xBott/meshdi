dependencies {

    api(project(":meshdi-moduleit:api"))

    testImplementation(project(":meshdi-api"))
    testImplementation(project(":meshdi-processor"))
    testImplementation(project(":meshdi-moduleit:processor"))

    testImplementation(libs.google.compile.testing)
    testImplementation(libs.bundles.kern.meta)

}