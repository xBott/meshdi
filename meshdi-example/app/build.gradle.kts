plugins {
    id("com.gradleup.shadow") version "9.2.0"
}

dependencies {

    implementation(project(":meshdi-api"))
    implementation(project(":meshdi-core"))

    implementation(project(":meshdi-moduleit:api"))
    implementation(project(":meshdi-moduleit:core"))

    implementation(libs.bundles.kern.default)
    implementation(libs.kern.version)

}

tasks.shadowJar {
    manifest {
        attributes(
            "Main-Class" to "me.bottdev.example.app.App"
        )
    }
    mergeServiceFiles()
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<Copy>("copyJar") {
    dependsOn(tasks.shadowJar)
    from(tasks.shadowJar)
    into("/Users/romanplakhotniuk/meshdi_tests")
}