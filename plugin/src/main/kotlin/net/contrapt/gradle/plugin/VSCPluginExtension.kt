package net.contrapt.gradle.plugin

data class VSCPluginExtension(
    var outFile: String = "vsc-gradle.json" // The file to write the model to
)
