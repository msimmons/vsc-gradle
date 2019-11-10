package net.contrapt.gradle.plugin

import org.gradle.testkit.runner.GradleRunner
import java.io.File

class VSCPluginTest {

    //@Test
    fun basicTest() {
        val result = GradleRunner.create()
                .withProjectDir(File("plugin/src/test/resources"))
                .withArguments("getModel", "--stacktrace")
                .withPluginClasspath()
                .build()
        println(result.output)
    }
}