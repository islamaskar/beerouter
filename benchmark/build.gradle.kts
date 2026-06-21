plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15"
}

kotlin {
    applyDefaultHierarchyTemplate()
    jvmToolchain(21)

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.maplibre.spatialk:geojson:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.15")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
            }
        }

        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("io.ktor:ktor-client-curl:3.1.1")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("io.ktor:ktor-client-cio:3.1.1")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
                implementation("com.github.abrensch.brouter:brouter-core:c09ddffad93f1d1667e3bbbf9977156447c3ec6f")
                implementation("com.github.abrensch.brouter:brouter-mapaccess:c09ddffad93f1d1667e3bbbf9977156447c3ec6f")
            }
        }
    }
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

benchmark {
    targets {
        register("jvm")
        register("linuxX64")
    }

    configurations {
        register("poiPerformance") {
            include("PoiPerformanceBenchmark")
            iterations = 3
            warmups = 1
            params["poiMode"] = mutableListOf("with-pois", "no-pois")
        }
    }
}

val routingTestSegmentsBase =
    rootProject.layout.buildDirectory.dir("routing-test/tuebingen-regbez")

afterEvaluate {
    tasks.named("jvmPoiPerformanceBenchmark") {
        dependsOn(
            ":generator:generateRoutingTestRd5WithPois",
            ":generator:generateRoutingTestRd5NoPois",
        )
        if (this is JavaExec) {
            systemProperty("beerouter.routingTestSegmentsBase", routingTestSegmentsBase.get().asFile.absolutePath)
        }
    }
}

tasks.register<JavaExec>("singleRoutePerformance") {
    description = "Runs route 9 gravel c=1.5 for focused performance checks"
    group = "verification"
    dependsOn("jvmMainClasses")
    mainClass.set("dev.skynomads.beerouter.benchmark.SingleRoutePerformance")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = files(jvmCompilation.output.allOutputs, jvmCompilation.runtimeDependencyFiles)
    project.findProperty("profileArgs")?.toString()?.split(' ')?.let { args = it }
    project.findProperty("jfrFile")?.toString()?.let { jfrFile ->
        jvmArgs("-XX:StartFlightRecording=filename=$jfrFile,settings=profile,dumponexit=true")
    }
}
