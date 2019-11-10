package net.contrapt.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject



class VSCPlugin : Plugin<Project> {

    val registry: ToolingModelBuilderRegistry

    @Inject
    constructor(registry: ToolingModelBuilderRegistry) {
        this.registry = registry
    }

    override fun apply(project: Project) {
        project.tasks.create("getModel", GetModelTask::class.java)
        registry.register(PluginModelBuilder())
    }

}