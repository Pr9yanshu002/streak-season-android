import org.gradle.api.tasks.Copy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    // Only the test-side fixture DTOs use serialization — domain/model is
    // plain Kotlin with no serialization dependency at all.
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// contract/fixtures/ is the shared source of truth for both clients. Copy it
// into a generated resources dir under build/ (never into src/) so it merges
// into build/resources/test/fixtures/ via the normal resources srcDir
// mechanism, the same way processTestResources merges src/test/resources/.
val fixturesResourcesDir = layout.buildDirectory.dir("generated/fixturesResources")

val copyFixtures by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory.dir("contract/fixtures"))
    into(fixturesResourcesDir.map { it.dir("fixtures") })
    include("*.json")
}

sourceSets {
    test {
        resources.srcDir(fixturesResourcesDir)
    }
}

tasks.processTestResources {
    dependsOn(copyFixtures)
}

tasks.test {
    dependsOn(copyFixtures)
    useJUnitPlatform()
    // So FixtureLoader's "Skipping placeholder fixture" lines show in the
    // Gradle output instead of only in the HTML report.
    testLogging.showStandardStreams = true
}
