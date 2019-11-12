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

        val tasks = mutableSetOf<String>()
        getTasks(tasks,"", project)
        val dependencies = mutableMapOf<String, PluginDependency>()
        getDependencies(dependencies, project)
        getSourceDependencies(project, dependencies)
        val dependencySource = PluginDependencySource("Gradle", project.path, dependencies.values.sorted().toMutableList())
        val classpaths = mutableSetOf<ClasspathData>()
        getClasspath(classpaths, project)

        return PluginModel.Impl(mutableListOf(dependencySource), classpaths, tasks)
    }

    fun getTasks(tasks: MutableCollection<String>, prefix: String, project: Project) : Collection<String> {
        project.childProjects.forEach {
            getTasks(tasks, project.name, it.value)
        }
        tasks.addAll(project.tasks.map { if (prefix.isBlank()) ":${it.name}" else ":${prefix}:${it.name}" })
        return tasks
    }

    fun getSourceDependencies(project: Project, dependencies: MutableMap<String, PluginDependency>) {
        project.configurations.create("vsc-gradle")
        dependencies.forEach {
            project.dependencies.add("vsc-gradle", "${it.key}:sources")
        }
        project.configurations.getByName("vsc-gradle").resolvedConfiguration.resolvedArtifacts.forEach {
            if (it.classifier == "sources") {
                val key = "${it.moduleVersion.id.group}:${it.moduleVersion.id.name}:${it.moduleVersion.id.version}"
                dependencies[key]?.sourceFileName = it.file.absolutePath
            }
        }
    }

    fun getDependencies(dependencies: MutableMap<String, PluginDependency>, project: Project) {
        project.childProjects.forEach {
            getDependencies(dependencies, it.value)
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
                        PluginDependency(artifact.file.absolutePath, null, null, group, name, version, mutableSetOf<String>(), mutableSetOf<String>(), isTransitive, false)
                    }
                    dep.scopes.add(config.name)
                    dep.modules.add(project.name)
                }
            }
            catch (e: Exception) {
                project.logger.debug("Error resolving ${config.name}", e)
            }
        }
    }

    fun getClasspath(classpaths: MutableCollection<ClasspathData>, project: Project) {
        project.childProjects.forEach {
            getClasspath(classpaths, it.value)
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
    }

}