package net.contrapt.gradle.plugin

import net.contrapt.jvmcode.model.DependencyData
import net.contrapt.jvmcode.model.DependencySourceData
import java.io.Serializable

class PluginDependencySource(
        override val name: String,
        override val description: String,
        override val dependencies: MutableCollection<DependencyData>
) : DependencySourceData, Serializable {
}