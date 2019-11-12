package net.contrapt.gradle.model

import net.contrapt.jvmcode.model.ClasspathData
import net.contrapt.jvmcode.model.DependencySourceData
import net.contrapt.jvmcode.model.JvmProjectData
import java.io.Serializable

interface PluginModel : JvmProjectData {

    val tasks: Collection<String>

    class Impl(
            override val dependencySources: Collection<DependencySourceData>,
            override val classDirs: Collection<ClasspathData>,
            override val tasks: Collection<String>
    ) : PluginModel, Serializable

}