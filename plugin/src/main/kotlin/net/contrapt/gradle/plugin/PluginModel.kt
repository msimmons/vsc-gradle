package net.contrapt.gradle.plugin

import net.contrapt.jvmcode.model.DependencySourceData
import net.contrapt.jvmcode.model.PathData
import net.contrapt.jvmcode.model.ProjectUpdateData
import java.io.Serializable

interface PluginModel : ProjectUpdateData {

    val tasks: Collection<String>
    val errors: Collection<PluginDiagnostic>

    class Impl(
            override val source: String,
            override val dependencySources: Collection<DependencySourceData>,
            override val paths: Collection<PathData>,
            override val tasks: Collection<String>,
            override val errors: Collection<PluginDiagnostic>
    ) : PluginModel, Serializable

    companion object {
        val SOURCE = "Gradle"
    }
}