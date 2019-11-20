package net.contrapt.gradle.model

import net.contrapt.jvmcode.model.ClasspathData
import net.contrapt.jvmcode.model.DependencySourceData
import net.contrapt.jvmcode.model.ProjectUpdateData
import java.io.Serializable

interface PluginModel : ProjectUpdateData {

    val tasks: Collection<String>
    val errors: Collection<String>

    class Impl(
            override val source: String,
            override val dependencySources: Collection<DependencySourceData>,
            override val classDirs: Collection<ClasspathData>,
            override val tasks: Collection<String>,
            override val errors: Collection<String>
    ) : PluginModel, Serializable

    companion object {
        val SOURCE = "Gradle"
    }
}