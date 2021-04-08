package com.github.pedromassangocode.magicx.services

import com.github.pedromassangocode.magicx.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
