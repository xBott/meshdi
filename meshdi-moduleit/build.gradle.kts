subprojects {

    dependencies {

        api(project(":meshdi-core"))
        annotationProcessor(project(":meshdi-processor"))

        api(rootProject.libs.kern.dependency.versioned)
        api(rootProject.libs.semver4j)

    }

}