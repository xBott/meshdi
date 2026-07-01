plugins {
    java
}

allprojects {

    group = "me.bottdev.meshdi"
    version = "0.0.1"

    repositories {
        mavenCentral()
        maven {
            name = "reposliteNimbraSnapshots"
            url = uri("https://reposlite.nimbra.net/snapshots")
        }
    }

}

subprojects {

    apply(plugin = "java")

}