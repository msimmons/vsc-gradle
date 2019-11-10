package net.contrapt.gradle.plugin

import net.contrapt.gradle.model.PluginModel
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Task to dump the current extension property definitions; useful for debugging
 */
open class GetModelTask : DefaultTask() {

    override fun getGroup() = "vsc-gradle"

    @TaskAction
    fun getModel() {
        val model = PluginModelBuilder().buildAll(PluginModel::class.java.name, project)
        model.dependencySources.forEach {
            it.dependencies.forEach {
                println("${it.groupId}:${it.artifactId}:${it.version} ${it.transitive} ${!it.sourceFileName.isNullOrBlank()}")
            }
        }
        model.classDirs.forEach {
            println("${it.name} ${it.module} \n   ${it.sourceDirs} \n   ${it.classDirs}")
        }
        model.tasks.forEach {
            println(it)
        }
    }
}