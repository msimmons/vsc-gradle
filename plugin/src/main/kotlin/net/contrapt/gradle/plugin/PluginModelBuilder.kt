package net.contrapt.gradle.plugin

import net.contrapt.gradle.model.PluginDiagnostic
import net.contrapt.gradle.model.PluginModel
import net.contrapt.jvmcode.model.PathData
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.tooling.provider.model.ToolingModelBuilder
import java.io.File
import java.lang.IllegalStateException

class PluginModelBuilder : ToolingModelBuilder {

    val sourceToOutputTags = listOf("java", "kotlin", "groovy", "scala", "resources", "antlr")

    override fun canBuild(modelName: String): Boolean {
        return modelName == PluginModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): PluginModel {
        val errors = mutableListOf<PluginDiagnostic>()
        val taskResult = runCatching {  getTasks(project) }
        val dependenciesResult = runCatching { getDependencies(project, errors) }
        val pathsResult = runCatching { getPathDatas(project, errors) }
        // Process any errors
        if (taskResult.isFailure) processException(project, errors, taskResult.exceptionOrNull())
        if (dependenciesResult.isFailure) processException(project, errors, dependenciesResult.exceptionOrNull())
        if (pathsResult.isFailure) processException(project, errors, pathsResult.exceptionOrNull())
        // Create the model
        val tasks = taskResult.getOrElse { emptyList() }
        val dependencies = dependenciesResult.getOrElse { emptyMap() }
        val classpaths = pathsResult.getOrElse { emptySet() }
        val description = "Gradle ${project.gradle.gradleVersion} (${project.buildFile.absolutePath})"
        val dependencySource = PluginDependencySource(PluginModel.SOURCE, description, dependencies.values.sorted())
        return PluginModel.Impl(PluginModel.SOURCE, listOf(dependencySource), classpaths, tasks, errors)
    }

    private fun processException(project: Project, errors: MutableList<PluginDiagnostic>, exception: Throwable?) {
        if (exception != null) errors.add(getDiagnostic(project, exception))
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

    private fun getDiagnostic(project: Project, thrown: Throwable, optional: String? = null): PluginDiagnostic {
        var curExc = thrown
        val message = StringBuilder()
        var location = processCause(curExc, message)
        while (curExc.cause != null) {
            curExc = curExc.cause as Throwable
            location = processCause(curExc, message)
        }
        if (location.first.isEmpty()) location = Pair(project.buildFile.absolutePath, location.second)
        if (optional != null) message.append(optional)
        return PluginDiagnostic.Impl(location.first, location.second, message.toString())
    }

    fun getTasks(project: Project, prefix: Array<String> = emptyArray(), tasks: MutableCollection<String> = mutableSetOf<String>()) : Collection<String> {
        project.childProjects.forEach {
            val parentPrefix = if (it.value == project.rootProject) prefix else prefix + it.value.name
            getTasks(it.value, parentPrefix, tasks)
        }
        val curPrefix = if ( prefix.size > 0 ) prefix.joinToString(":", ":", ":") { it } else ""
        tasks.addAll(project.tasks.map { "${curPrefix}${it.name}" })
        // Add the un-qualified tasks as well
        if ( !curPrefix.isEmpty()) tasks.addAll(project.tasks.map { it.name })
        return tasks
    }

    fun resolveSourceArtifacts(project: Project, dependencies: Map<String, PluginDependency>, errors: MutableList<PluginDiagnostic>) {
        val config = project.configurations.create("${project.name}-vsc-gradle")
        dependencies.forEach {
            project.dependencies.add(config.name, "${it.key}:sources")
        }
        try {
            config.resolvedConfiguration.lenientConfiguration.artifacts.forEach {
                project.logger.lifecycle("Source artifact ${it.moduleVersion.id.name}")
                if (it.classifier == "sources") {
                    val key = "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}"
                    dependencies[key]?.sourceFileName = it.file.absolutePath
                }
            }
        }
        catch (e: Exception) {
            errors.add(getDiagnostic(project, e))
        }
        errors.addAll(config.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies
                .filter {
                    it.selector.module.group != project.group
                }
                .map {
                    val optional = "Unresolved Source: ${it.selector.module.group}:${it.selector.module.name} in ${project.group}:${project.name}"
                    getDiagnostic(project, it.problem, optional)
                }
        )
    }

    fun getDependencies(project: Project, errors: MutableList<PluginDiagnostic>, dependencies: MutableMap<String, PluginDependency> = mutableMapOf()) : Map<String, PluginDependency> {
        project.childProjects.forEach {
            dependencies.putAll(getDependencies(it.value, errors, dependencies))
        }
        val projectDependencies = mutableMapOf<String, PluginDependency>()
        project.configurations.filter { it.isCanBeResolved }.forEach {config ->
            val statedDependencies = config.allDependencies.map {
                "${it.group}:${it.name}:${it.version}"
            }
            try {
                config.resolvedConfiguration.lenientConfiguration.artifacts.forEach { artifact ->
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
                processException(project, errors, e)
            }
            errors.addAll(config.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies
                    .filter {
                        it.selector.module.group != project.group
                    }
                    .map {
                        val optional = "Unresolved: ${it.selector.module.group}:${it.selector.module.name} in ${project.group}:${project.name}"
                        getDiagnostic(project, it.problem, optional)
                    }
            )
        }
        resolveSourceArtifacts(project, projectDependencies, errors)
        return dependencies + projectDependencies
    }

    fun getPathDatas(project: Project, errors: MutableList<PluginDiagnostic>, pathDatas: MutableSet<PathData> = mutableSetOf()) : Set<PathData> {
        project.childProjects.forEach {
            getPathDatas(it.value, errors, pathDatas)
        }
        project.convention.plugins.forEach {
            project.logger.lifecycle("Got plugin ${it.key} ${it.value}")
            val convention = it.value
            if (convention is JavaPluginConvention) {
                convention.sourceSets.forEach {ss ->
                    val classDirMap = ss.output.files.associate { dir ->
                        val key = sourceToOutputTags.firstOrNull { dir.path.contains(it) } ?: ""
                        key to dir.absolutePath
                    }
                    ss.allSource.srcDirs.forEach { dir ->
                        val key = sourceToOutputTags.firstOrNull { dir.path.contains(it) } ?: ""
                        pathDatas.add(PluginPath(PluginModel.SOURCE, ss.name, project.name, dir.absolutePath, classDirMap[key] ?: ""))
                    }
                }
            }
            else {
                project.logger.debug("Skipping convention ${convention::class.java}")
            }
        }
        return pathDatas
    }

}