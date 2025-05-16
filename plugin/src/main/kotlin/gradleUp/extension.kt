@file:Suppress("UNCHECKED_CAST")


import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.domainObjectSet
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.property
import javax.inject.Inject
import kotlin.collections.joinToString

abstract class RepublishExtension {

    @get:Inject
    abstract val objects: ObjectFactory

    // Define a domain object set to hold strings
    internal val libraries = objects.domainObjectSet(Library::class)

    fun library(action: Action<Library>) {
        val library = objects.newInstance<Library>()
        action.execute(library)
        libraries += library
    }
}

abstract class Library {

    @get:Inject
    abstract val objects: ObjectFactory

    val from = objects.newInstance<From>()
    val into = objects.newInstance<Into>(from)
    fun from(action: Action<From>) = action.execute(from)
    fun into(action: Action<Into>) = action.execute(into)
    var classifierExtractor = Regex(".*-natives-([^-]+)-(.+?)\\.jar")

    abstract class From {

        @get:Inject
        abstract val objects: ObjectFactory

        val repo = objects.property<String>()

        val group = objects.property<String>()
        val artifact = objects.property<String>()
        val version = objects.property<String>()
        var gav: String = ""
            set(value) {
                val (g, a, v) = value.split(':')
                group.set(g)
                artifact.set(a)
                version.set(v)
                field = value
            }
        val natives = objects.listProperty<String>()
    }

    abstract class Into @Inject constructor(@Internal val from: From) {

        @get:Inject
        abstract val objects: ObjectFactory

        val repo = objects.directoryProperty()

        val group = objects.property<String>()
            get() = field.takeIf { it.isPresent } ?: from.group
        val artifact = objects.property<String>()
            get() = field.takeIf { it.isPresent } ?: from.artifact
        val version = objects.property<String>()
            get() = field.takeIf { it.isPresent } ?: from.version
        val gav: String
            get() = "${group.get()}:${artifact.get()}:${version.get()}"
    }

    val buildGradleKts: String
        get() = buildString {
            appendLine("""
                import gradleUp.*
                
                plugins {
                    java
                    `maven-publish`
                    id("io.github.gradleUp.os-arch") version "0.1.5"
                }

                version = "0.0.1"

                repositories {
                    mavenCentral()""".trimIndent())
            if (from.repo.isPresent)
                appendLine("""    maven("${from.repo.get()}")""")
            append("""
                }
                val libraryDep = configurations.dependencyScope("libraryDep")
                val nativesDep = configurations.dependencyScope("nativesDep")
                
                dependencies {
                    for (native in listOf(${from.natives.get().joinToString { "\"$it\"" }}))
                        nativesDep("${from.gav}:${'$'}native")
                    libraryDep("${from.gav}")
                }
                val nativesRes = configurations.resolvable("nativesRes") { extendsFrom(nativesDep.get()) }
                val libraryRes = configurations.resolvable("libraryRes") { extendsFrom(libraryDep.get()) }
                
                // remove any additional transitive dependencies
                val natives = nativesRes.get().resolve().filter { "${from.artifact.get()}" in it.name }
                //natives.forEach { println(it) }
                val library = libraryRes.get().resolve().first()
                
                dependencies {
                    runtimeOnlyNatives("${from.group.get()}:${from.artifact.get()}")
                }
                
                for (native in natives) {
                    val platform = native getPlatform ""${'"'}$classifierExtractor""${'"'}
                    platform.apply { addVariant(native, "${into.gav}") }
                }
                
                configurations {
                    apiElements.onlyArtifact = library
                    runtimeElements.onlyArtifact = library
                }

                publishing {
                    publications.create<MavenPublication>("maven") {
                        suppressAllPomMetadataWarnings()
                        from(components["java"])
                        groupId = "${into.group.get()}"
                        artifactId = "${into.artifact.get()}"
                        version = "${into.version.get()}"
                    }
                    repositories.maven {
                        name = "repo"
                        url = uri("${into.repo.get()}")
                    }
                }
                tasks.named<GenerateModuleMetadata>("generateMetadataFileForMavenPublication") {
                    suppressedValidationErrors.add("dependencies-without-versions")
                }""".trimIndent())
        }
}