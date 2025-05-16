package gradleUp

import RepublishExtension
import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.kotlin.dsl.create
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.io.RandomAccessFile
import java.sql.DriverManager
import kotlin.io.path.createFile
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

/**
 * A simple 'hello world' plugin.
 */
class VariantRepublisherPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val settings = project.extensions.create<RepublishExtension>("republish")
        project.tasks.register("republish") {
            doLast {
                for (library in settings.libraries) {
//                    println("Publishing ${library.from.gav} to ${library.into.repo.get()}")
                    val dir = createTempDirectory()
                    dir.resolve("settings.gradle.kts").createFile().writeText("rootProject.name = \"republisher\"")
                    dir.resolve("build.gradle.kts").createFile().writeText(library.buildGradleKts)

                    // Run the build
                    val runner = GradleRunner.create()
                    runner.forwardOutput()
                    //                runner.withPluginClasspath()
                    runner.withArguments("publishMavenPublicationToRepublisherRepository")
                    runner.withProjectDir(dir.toFile())
                    //                println(runner.pluginClasspath)
                    /*val result =*/ runner.build()
//                    println(result.output)
                }
            }
        }
    }
}