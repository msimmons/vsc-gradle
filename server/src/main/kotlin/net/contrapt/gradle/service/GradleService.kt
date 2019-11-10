package net.contrapt.gradle.service

import net.contrapt.gradle.model.PluginModel
import net.contrapt.jvmcode.model.ClasspathData
import net.contrapt.jvmcode.model.DependencySourceData
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.OperationResult
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.test.TestFinishEvent
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Created by mark on 6/17/17.
 */
class GradleService(val projectDir: String, val extensionDir: String) {

    private val gradleConnector = GradleConnector.newConnector()
    private val connection: ProjectConnection
    private val pluginModelBuilder: ModelBuilder<PluginModel>
    private var pluginModel : PluginModel

    init {
        gradleConnector.useBuildDistribution()
        gradleConnector.forProjectDirectory(File(projectDir))
        connection = gradleConnector.connect()

        pluginModelBuilder = connection.model(PluginModel::class.java)
        pluginModelBuilder.setEnvironmentVariables(mutableMapOf("REPO_DIR" to "$extensionDir/out/m2/repo"))
        pluginModelBuilder.withArguments("--init-script", "${extensionDir}/out/m2/init.gradle")
        pluginModel = pluginModelBuilder.get()
    }

    /**
     * Refresh the pluginModel model
     */
    fun refresh() {
        pluginModel = pluginModelBuilder.get()
    }

    fun getTasks() : Collection<String> {
        return pluginModel.tasks
    }

    /**
     * Get dependencies sources
     */
    fun getDependencySources() : Collection<DependencySourceData> {
        return pluginModel.dependencySources
    }

    /**
     * Get class source and output directories
     */
    fun getClasspath() : Collection<ClasspathData> {
        return pluginModel.classDirs
    }

    /**
     * Run the given task
     */
    fun runTask(taskName: String) : String {
        val os = ByteArrayOutputStream()
        connection.newBuild()
                .forTasks(taskName)
                .setStandardOutput(os)
                .setStandardError(os)
                .withArguments("--stacktrace", "-Dkotlin.compiler.execution.strategy=\"in-process\"")
                .addProgressListener({event: ProgressEvent ->  println("PROGRESS: ${event.displayName}")})
                .run()
        return String(os.toByteArray())
    }

    /**
     * Run the given test class and return ?
     */
    fun runTest(className: String) {
        val os = ByteArrayOutputStream()
        try {
            connection.newTestLauncher()
                    .withJvmTestClasses(className)
                    .setStandardOutput(os)
                    .setStandardError(os)
                    .withArguments("--stacktrace")
                    .addProgressListener(progressListener())
                    .run()
        }
        catch (e: Exception) {
            println("Test Exception: $e")
            println("Cause: ${e.cause}")
        }
        println("OUTPUT:\n ${String(os.toByteArray())}")
    }

    private fun progressListener() = {event: ProgressEvent ->
        when ( event ) {
            is TestFinishEvent -> {println(event.descriptor.name); handleResult(event.result)}
        }
    }

    private fun handleResult(result: OperationResult) {
        when ( result ) {
            is FailureResult -> println("TEST FAIL: ${result.failures.first().description}")// ${result.failures.joinToString { it.message }}")
        }
    }

}