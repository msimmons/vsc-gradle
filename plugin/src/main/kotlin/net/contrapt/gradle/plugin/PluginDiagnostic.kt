package net.contrapt.gradle.plugin

import java.io.Serializable

interface PluginDiagnostic {

    val file: String
    val line: Int
    val message: String

    data class Impl(
            override val file: String,
            override val line: Int,
            override val message: String
    ) : PluginDiagnostic, Serializable

}