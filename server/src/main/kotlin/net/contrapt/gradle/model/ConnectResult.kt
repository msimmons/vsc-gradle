package net.contrapt.gradle.model

/**
 * The result of connecting to a gradle project for consumption by the gradle extension
 */
class ConnectResult(
        val tasks: Collection<String>,
        val errors: Collection<PluginDiagnostic>
)
