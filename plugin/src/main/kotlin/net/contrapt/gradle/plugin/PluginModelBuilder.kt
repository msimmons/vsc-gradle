package net.contrapt.gradle.plugin

import net.contrapt.gradle.model.PluginDiagnostic
import net.contrapt.gradle.model.PluginModel
import net.contrapt.jvmcode.model.PathData
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.tooling.provider.model.ToolingModelBuilder

class PluginModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean {
        return modelName == PluginModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): PluginModel {
        val errors = mutableListOf<PluginDiagnostic>()
        val taskResult = runCatching {  getTasks("", project) }
        val dependenciesResult = runCatching { getDependencies(project, errors) }
        val pathsResult = runCatching { getPathDatas(project, errors) }
        // Process any errors
        if (taskResult.isFailure) processException(errors, taskResult.exceptionOrNull())
        if (dependenciesResult.isFailure) processException(errors, dependenciesResult.exceptionOrNull())
        if (pathsResult.isFailure) processException(errors, pathsResult.exceptionOrNull())
        // Create the model
        val tasks = taskResult.getOrElse { emptyList() }
        val dependencies = dependenciesResult.getOrElse { emptyMap() }
        val classpaths = pathsResult.getOrElse { emptySet() }
        val description = "Gradle ${project.gradle.gradleVersion} (${project.buildFile.absolutePath})"
        val dependencySource = PluginDependencySource(PluginModel.SOURCE, description, dependencies.values.sorted())
        return PluginModel.Impl(PluginModel.SOURCE, listOf(dependencySource), classpaths, tasks, errors)
    }

    private fun processException(errors: MutableList<PluginDiagnostic>, exception: Throwable?) {
        if (exception != null) errors.add(getDiagnostic(exception))
    }

    private fun processCause(e: Throwable, message: StringBuilder): Pair<String, Int> {
        message.append("${e.message}\n")
        return when (e) {
            is LocationAwareException -> {
                (e.location ?: "") to (e.lineNumber ?: 0)
            }
            else -> "" to 0
        }
    }

    private fun getDiagnostic(thrown: Throwable): PluginDiagnostic {
        var curExc = thrown
        val message = StringBuilder()
        println("Current Exception: ${curExc::class.java} ${curExc::class} ${curExc is LocationAwareException}")
        var location = processCause(curExc, message)
        while (curExc.cause != null) {
            curExc = curExc.cause as Throwable
            location = processCause(curExc, message)
        }
        return PluginDiagnostic.Impl(location.first, location.second, message.toString())
    }

    fun getTasks(prefix: String, project: Project, tasks: MutableCollection<String> = mutableSetOf<String>()) : Collection<String> {
        project.childProjects.forEach {
            getTasks(project.name, it.value, tasks)
        }
        tasks.addAll(project.tasks.map { if (prefix.isBlank()) ":${it.name}" else ":${prefix}:${it.name}" })
        return tasks
    }

    fun resolveSourceArtifacts(project: Project, dependencies: Map<String, PluginDependency>, errors: MutableList<PluginDiagnostic>) {
        val config = project.configurations.create("vsc-gradle")
        dependencies.forEach {
            project.dependencies.add(config.name, "${it.key}:sources")
        }
        try {
            config.resolvedConfiguration.lenientConfiguration.artifacts.forEach {
                if (it.classifier == "sources") {
                    val key = "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}"
                    dependencies[key]?.sourceFileName = it.file.absolutePath
                }
            }
        }
        catch (e: Exception) {
            errors.add(getDiagnostic(e))
            //project.logger.debug("Error resolving source configuration", e)
        }
        errors.addAll(config.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.map {
            getDiagnostic(it.problem)
        })
    }

    fun getDependencies(project: Project, errors: MutableList<PluginDiagnostic>, dependencies: MutableMap<String, PluginDependency> = mutableMapOf()) : Map<String, PluginDependency> {
        project.childProjects.forEach {
            dependencies.putAll(getDependencies(it.value, errors, dependencies))
        }
        val projectDependencies = mutableMapOf<String, PluginDependency>()
        project.configurations.filter {it.isCanBeResolved}.forEach {config ->
            val statedDependencies = config.allDependencies.map {
                "${it.group}:${it.name}:${it.version}"
            }
            try {
                config.resolvedConfiguration.resolvedArtifacts.filter { it.moduleVersion.id.group != project.rootProject.name }.forEach { artifact ->
                    val group = artifact.moduleVersion.id.group
                    val name = artifact.moduleVersion.id.name
                    val version = artifact.moduleVersion.id.version
                    val key = "$group:$name:$version"
                    val isTransitive = !statedDependencies.contains(key)
                    val dep = projectDependencies.getOrPut(key) {
                        PluginDependency(artifact.file.absolutePath, null, null, group, name, version, mutableSetOf(), mutableSetOf(), isTransitive, false)
                    }
                    dep.scopes.add(config.name)
                    dep.modules.add(project.name)
                }
            }
            catch (e: Exception) {
                processException(errors, e)
            }
        }
        resolveSourceArtifacts(project, projectDependencies, errors)
        return dependencies + projectDependencies
    }

    fun getPathDatas(project: Project, errors: MutableList<PluginDiagnostic>, classpaths: MutableSet<PathData> = mutableSetOf()) : Set<PathData> {
        project.childProjects.forEach {
            getPathDatas(it.value, errors, classpaths)
        }
        project.convention.plugins.forEach {
            val convention = it.value
            if (convention is JavaPluginConvention) {
                convention.sourceSets.forEach {ss ->
                    val cp = PluginPath(PluginModel.SOURCE, ss.name, project.name)
                    cp.sourceDirs.addAll(ss.allSource.srcDirs.map { it.absolutePath })
                    cp.classDirs.addAll(ss.output.files.map { it.absolutePath })
                    classpaths.add(cp)
                }
            }
            else {
                project.logger.debug("Skipping convention ${convention::class.java}")
            }
        }
        return classpaths
    }

}