dependencies {

    implementation(project(":meshdi-api"))
    implementation(project(":meshdi-core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)

    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit.jupiter)

    testImplementation(libs.assertj.core)

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.bundles.kern.default)
    implementation(libs.kern.version)

}