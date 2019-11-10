package net.contrapt.gradle.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

/**
 * Created by mark on 6/17/17.
 */
class GradleServiceSpec {

    val service = GradleService("/home/mark/work/vsc-gradle/server/src/test/resources/test-project", "/home/mark/work/vsc-gradle")

    @Test
    fun testSomething() {
        service.getTasks()
    }

    @Test
    fun badTask() {
        assertThrows(Exception::class.java) {
            service.runTask("bad")
        }
    }

    @Test()
    fun badTest() {
        service.runTest("net.contrapt.FooSpec")
    }

    @Test
    fun failingTest() {
        service.runTest("net.contrapt.TestTest")
    }

    @Test
    fun getDependencies() {
        val dependencies = service.getDependencySources()
        println(dependencies)
    }

    @Test
    fun getPaths() {
        val paths = service.getClasspath()
        println(paths)
    }

    @Test
    fun refresh() {
        val c1 = service.getClasspath()
        service.refresh()
        assertEquals(c1.size, service.getClasspath().size)
    }

}