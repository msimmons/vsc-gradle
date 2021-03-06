package net.contrapt.gradle.plugin

import net.contrapt.gradle.model.PluginModel
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Task to dump the current extension property definitions; useful for debugging
 */
open class GetModelTask : DefaultTask() {


    @Input
    override fun getGroup() = "vsc-gradle"

    @TaskAction
    fun getModel() {
        val model = PluginModelBuilder().buildAll(PluginModel::class.java.name, project)
        model.dependencySources.forEach {
            it.dependencies.forEach {
                println("${it.groupId}:${it.artifactId}:${it.version} ${it.transitive} ${!it.sourceFileName.isNullOrBlank()}")
            }
        }
        model.paths.forEach {
            println("${it.name} ${it.module} \n   ${it.sourceDir} \n   ${it.classDir}")
        }
        model.tasks.forEach {
            println(it)
        }
        model.errors.forEach {
            println(it)
        }
    }
}