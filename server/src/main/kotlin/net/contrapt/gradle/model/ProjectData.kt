package net.contrapt.gradle.model

import net.contrapt.jvmcode.model.ClasspathData
import net.contrapt.jvmcode.model.DependencySourceData
import net.contrapt.jvmcode.model.JvmProjectData

data class ProjectData(
        override val dependencySources: Collection<DependencySourceData>,
        override val classDirs: Collection<ClasspathData>,
        override val classpath: String
) : JvmProjectData