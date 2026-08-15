dependencies {

    implementation(project(":meshdi-api"))
    implementation(project(":meshdi-core"))
    implementation(project(":meshdi-moduleit:api"))

    testImplementation(project(":meshdi-api"))
    testImplementation(project(":meshdi-processor"))
    testImplementation(project(":meshdi-moduleit:processor"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)

    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    testImplementation(libs.assertj.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.bundles.kern.default)
    implementation(libs.kern.version)
    implementation(libs.bundles.kern.meta)

    testImplementation(libs.google.compile.testing)

    implementation(libs.google.auto.service)
    implementation(libs.java.poet)

    annotationProcessor(libs.google.auto.service)

}