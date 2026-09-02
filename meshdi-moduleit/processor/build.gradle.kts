dependencies {

    api(project(":meshdi-moduleit:api"))

    api(libs.bundles.kern.meta)
    testImplementation(libs.google.compile.testing)
    implementation(libs.google.auto.service)
    annotationProcessor(libs.google.auto.service)
    implementation(libs.java.poet)

}