package net.contrapt.gradle.plugin

import net.contrapt.jvmcode.model.PathData
import java.io.Serializable

class PluginPath(
        override val source: String,
        override val name: String,
        override val module: String,
        override val sourceDir: String,
        override val classDir: String
) : PathData, Serializable {
}