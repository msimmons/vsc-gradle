package net.contrapt.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject



class VSCPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        // Properties
        val extension = VSCPluginExtension()
        project.extensions.add("VSCGradle", extension)

        // Tasks
        project.tasks.create("getModel", GetModelTask::class.java)
    }

}