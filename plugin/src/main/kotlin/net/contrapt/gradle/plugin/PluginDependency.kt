package net.contrapt.gradle.plugin

import net.contrapt.jvmcode.model.DependencyData
import java.io.Serializable

class PluginDependency(
        override val fileName: String,
        override var sourceFileName: String?,
        override val jmod: String?,
        override val groupId: String,
        override val artifactId: String,
        override val version: String,
        override val scopes: MutableSet<String>,
        override val modules: MutableSet<String>,
        override val transitive: Boolean,
        override var resolved: Boolean
) : DependencyData, Serializable {
}