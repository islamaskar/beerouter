plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.configureEach {
        if (name.startsWith("transform") && name.endsWith("DependenciesMetadata")) {
            // These transformed klibs live under build/ and can disappear between
            // runs, which leaves Kotlin metadata compile tasks with stale input paths.
            outputs.upToDateWhen { false }
        }
    }
}
