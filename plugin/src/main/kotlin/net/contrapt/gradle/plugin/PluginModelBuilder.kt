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
        val tasks = getTasks("", project)
        val dependencies = getDependencies(project)
        resolveSourceArtifacts(project, dependencies)
        val dependencySource = PluginDependencySource("Gradle:${project.gradle.gradleVersion}", project.path, dependencies.values.sorted().toMutableList())
        val classpaths = getClasspathDatas(project)
        return PluginModel.Impl(listOf(dependencySource), classpaths, tasks)
    }

    fun getTasks(prefix: String, project: Project, tasks: MutableCollection<String> = mutableSetOf<String>()) : Collection<String> {
        project.childProjects.forEach {
            getTasks(project.name, it.value, tasks)
        }
        tasks.addAll(project.tasks.map { if (prefix.isBlank()) ":${it.name}" else ":${prefix}:${it.name}" })
        return tasks
    }

    fun resolveSourceArtifacts(project: Project, dependencies: Map<String, PluginDependency>) {
        project.configurations.create("vsc-gradle")
        dependencies.forEach {
            project.dependencies.add("vsc-gradle", "${it.key}:sources")
        }
        try {
            project.configurations.getByName("vsc-gradle").resolvedConfiguration.resolvedArtifacts.forEach {
                if (it.classifier == "sources") {
                    val key = "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}"
                    dependencies[key]?.sourceFileName = it.file.absolutePath
                }
            }
        }
        catch (e: Exception) {
            project.logger.debug("Error resolving source configuration", e)
        }
    }

    fun getDependencies(project: Project, dependencies: MutableMap<String, PluginDependency> = mutableMapOf()) : Map<String, PluginDependency> {
        project.childProjects.forEach {
            getDependencies(it.value, dependencies)
        }
        project.configurations.forEach {config ->
            val statedDependencies = config.allDependencies.map {
                "${it.group}:${it.name}:${it.version}"
            }
            try {
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val group = artifact.moduleVersion.id.group
                    val name = artifact.moduleVersion.id.name
                    val version = artifact.moduleVersion.id.version
                    val key = "$group:$name:$version"
                    val isTransitive = !statedDependencies.contains(key)
                    val dep = dependencies.getOrPut(key) {
                        PluginDependency(artifact.file.absolutePath, null, null, group, name, version, mutableSetOf(), mutableSetOf(), isTransitive, false)
                    }
                    dep.scopes.add(config.name)
                    dep.modules.add(project.name)
                }
            }
            catch (e: Exception) {
                project.logger.debug("Error resolving ${config.name}", e)
            }
        }
        return dependencies
    }

    fun getClasspathDatas(project: Project, classpaths: MutableSet<ClasspathData> = mutableSetOf()) : Set<ClasspathData> {
        project.childProjects.forEach {
            getClasspathDatas(it.value, classpaths)
        }
        project.convention.plugins.forEach {
            val convention = it.value
            if (convention is JavaPluginConvention) {
                convention.sourceSets.forEach {ss ->
                    val cp = PluginClasspath("Gradle", ss.name, project.name)
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