dependencies {

    api(project(":meshdi-moduleit:api"))
    implementation(rootProject.libs.maven.resolver.supplier)

    testImplementation(project(":meshdi-api"))
    testImplementation(project(":meshdi-processor"))
    testImplementation(project(":meshdi-moduleit:processor"))

    testImplementation(libs.google.compile.testing)
    testImplementation(libs.bundles.kern.meta)

}