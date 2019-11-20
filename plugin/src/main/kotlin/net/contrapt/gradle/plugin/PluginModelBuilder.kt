package net.contrapt.gradle.plugin

import net.contrapt.gradle.model.PluginModel
import net.contrapt.jvmcode.model.ClasspathData
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.tooling.provider.model.ToolingModelBuilder

class PluginModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean {
        return modelName == PluginModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): PluginModel {
        val errors = mutableListOf<String>()
        val tasks = getTasks("", project)
        val dependencies = getDependencies(project, errors)
        val description = "Gradle ${project.gradle.gradleVersion} (${project.buildFile.absolutePath})"
        val dependencySource = PluginDependencySource(PluginModel.SOURCE, description, dependencies.values.sorted())
        val classpaths = getClasspathDatas(project, errors)
        return PluginModel.Impl(PluginModel.SOURCE, listOf(dependencySource), classpaths, tasks, errors)
    }

    fun getTasks(prefix: String, project: Project, tasks: MutableCollection<String> = mutableSetOf<String>()) : Collection<String> {
        project.childProjects.forEach {
            getTasks(project.name, it.value, tasks)
        }
        tasks.addAll(project.tasks.map { if (prefix.isBlank()) ":${it.name}" else ":${prefix}:${it.name}" })
        return tasks
    }

    fun resolveSourceArtifacts(project: Project, dependencies: Map<String, PluginDependency>, errors: MutableList<String>) {
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
            errors.add("${e.message}: ${e.cause?.message ?: ""}")
            project.logger.debug("Error resolving source configuration", e)
        }
        errors.addAll(config.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.map { it.problem.message ?: "" })
    }

    fun getDependencies(project: Project, errors: MutableList<String>, dependencies: MutableMap<String, PluginDependency> = mutableMapOf()) : Map<String, PluginDependency> {
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
                errors.add("${e.message}: ${e.cause?.message ?: ""}")
                project.logger.debug("Error resolving ${config.name}", e)
            }
        }
        resolveSourceArtifacts(project, projectDependencies, errors)
        return dependencies + projectDependencies
    }

    fun getClasspathDatas(project: Project, errors: MutableList<String>, classpaths: MutableSet<ClasspathData> = mutableSetOf()) : Set<ClasspathData> {
        project.childProjects.forEach {
            getClasspathDatas(it.value, errors, classpaths)
        }
        project.convention.plugins.forEach {
            val convention = it.value
            if (convention is JavaPluginConvention) {
                convention.sourceSets.forEach {ss ->
                    val cp = PluginClasspath(PluginModel.SOURCE, ss.name, project.name)
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