package net.contrapt.gradle.plugin

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Task to dump the current extension property definitions; useful for debugging
 */
open class GetModelTask : DefaultTask() {


    @Input
    override fun getGroup() = "vsc-gradle"

    @TaskAction
    fun getModel() {
        val extension = project.extensions.findByType(VSCPluginExtension::class.java) ?: VSCPluginExtension()
        val model = PluginModelBuilder().build(project)
        File(extension.outFile).writeText((ObjectMapper().writeValueAsString(model)))
    }
}