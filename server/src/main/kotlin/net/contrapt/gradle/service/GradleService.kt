package net.contrapt.gradle.service

import net.contrapt.gradle.model.*
import net.contrapt.jvmcode.model.DependencySourceData
import net.contrapt.jvmcode.model.PathData
import org.gradle.internal.exceptions.LocationAwareException
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
class GradleService(val request: ConnectRequest) {

    //Settings file '/home/mark/work/vsc-gradle/server/src/test/resources/test-project/settings.gradle' line: 2
    private val LAE_PATTERN = ".*'(.*)'\\s?line:\\s?([0-9]+)\\s?.*".toRegex().toPattern()

    private val gradleConnector = GradleConnector.newConnector()
    private val connection: ProjectConnection
    private val pluginModelBuilder: ModelBuilder<PluginModel>
    lateinit private var pluginModel : PluginModel

    init {
        gradleConnector.useBuildDistribution()
        gradleConnector.forProjectDirectory(File(request.projectDir))
        connection = gradleConnector.connect()

        pluginModelBuilder = connection.model(PluginModel::class.java)
        pluginModelBuilder.setEnvironmentVariables(mutableMapOf("REPO_DIR" to "${request.extensionDir}/out/m2/repo"))
        pluginModelBuilder.withArguments("--init-script", "${request.extensionDir}/out/m2/init.gradle")
    }

    /**
     * Refresh the pluginModel model
     */
    fun refresh() : Pair<ConnectResult, ProjectData> {
        pluginModel = pluginModelBuilder.get()
        val result = ConnectResult(getTasks(), pluginModel.errors)
        val data = ProjectData(pluginModel.source, pluginModel.dependencySources, pluginModel.paths)
        return result to data
    }

    fun getTasks() : Collection<String> {
        return pluginModel.tasks.sortedBy { if (it.startsWith(":")) "9:$it" else "0:$it" }
    }

    /**
     * Get dependencies sources
     */
    fun getDependencySources() : Collection<DependencySourceData> {
        if (!::pluginModel.isInitialized) refresh()
        return pluginModel.dependencySources
    }

    /**
     * Get class source and output directories
     */
    fun getClasspath() : Collection<PathData> {
        if (!::pluginModel.isInitialized) refresh()
        return pluginModel.paths
    }

    /**
     * Run the given task
     */
    fun runTask(taskName: String) : String {
        val os = ByteArrayOutputStream()
        connection.newBuild()
                .forTasks(*taskName.split(" ").toTypedArray())
                .setStandardOutput(System.out)
                .setStandardError(System.out)
                .withArguments("--stacktrace", "-Dkotlin.compiler.execution.strategy=\"in-process\"")
                .addProgressListener({event: ProgressEvent ->
                })
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

    private fun processCause(e: Throwable, message: StringBuilder): Pair<String, Int>? {
        message.append("${e.message}\n")
        return when (e::class.java.name) {
            LocationAwareException::class.java.name -> {
                val matcher = LAE_PATTERN.matcher(e.message ?: "")
                if (matcher.matches()) {
                    val file = matcher.group(1)
                    val line = matcher.group(2).toInt()
                    file to line
                }
                else null
            }
            else -> null
        }
    }

    fun getDiagnostic(thrown: Throwable): PluginDiagnostic {
        var curExc = thrown
        val message = StringBuilder()
        var location = "" to 0
        println("Current Exception: ${curExc::class.java} ${curExc::class} ${curExc is LocationAwareException}")
        location = processCause(curExc, message) ?: location
        while (curExc.cause != null) {
            curExc = curExc.cause as Throwable
            location = processCause(curExc, message) ?: location
        }
        return PluginDiagnostic.Impl(location.first, location.second, message.toString())
    }
}