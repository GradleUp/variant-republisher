plugins { // Apply the Java Gradle plugin development plugin to add support for developing Gradle plugins
    `java-gradle-plugin`

    // Apply the Kotlin JVM plugin to add support for Kotlin.
    embeddedKotlin("jvm")

    id("com.gradle.plugin-publish") version "1.2.1"

    `kotlin-dsl`

    id("io.github.gradleUp.os-arch") version "0.1.5"
}

group = "io.github.gradleUp"
version = "0.0.1"

repositories { // Use Maven Central for resolving dependencies.
    mavenCentral()
}

kotlin.jvmToolchain(8)

dependencies {
    implementation(gradleTestKit())
}

testing {
    suites { // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) { // Use Kotlin Test test framework
            useKotlinTest(embeddedKotlinVersion)
        }

        // Create a new test suite
        val functionalTest by registering(JvmTestSuite::class) { // Use Kotlin Test test framework
            useKotlinTest(embeddedKotlinVersion)

            dependencies { // functionalTest test suite depends on the production code in tests
                implementation(project())
            }

            targets {
                all { // This test suite should run after the built-in test suite has run its tests
                    testTask.configure { shouldRunAfter(test) }
                }
            }
        }
    }
}

gradlePlugin {
    website = "https://github.com/GradleUp/variant-republisher"
    vcsUrl = website
    // Define the plugin
    plugins.create("variant-republisher") {
        id = "io.github.gradleUp.variant-republisher"
        implementationClass = "gradleUp.VariantRepublisherPlugin"
        displayName = "variant-republisher"
        description = "Gradle plugin to republish projects with native libraries to fix their Gradle Metadata support"
        tags = listOf("native", "library", "metadata", "variant")
    }
}

gradlePlugin.testSourceSets.add(sourceSets["functionalTest"])

tasks.check { // Include functionalTest as part of the check lifecycle
    dependsOn(testing.suites.named("functionalTest"))
}