plugins {
    id("java")
}

group = "me.bottdev.meshdi"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation(libs.google.compile.testing)

    implementation(project(":meshdi-api"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(libs.bundles.kern.default)
    implementation(libs.bundles.kern.meta)
    implementation(libs.google.auto.service)

    annotationProcessor(libs.google.auto.service)

}

tasks.test {
    useJUnitPlatform()
}