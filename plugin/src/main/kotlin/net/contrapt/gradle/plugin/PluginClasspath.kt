package net.contrapt.gradle.plugin

import net.contrapt.jvmcode.model.ClasspathData
import java.io.Serializable

class PluginClasspath(
        override val source: String,
        override val name: String,
        override val module: String,
        override val sourceDirs: MutableSet<String> = mutableSetOf(),
        override val classDirs: MutableSet<String> = mutableSetOf()
) : ClasspathData, Serializable {
}