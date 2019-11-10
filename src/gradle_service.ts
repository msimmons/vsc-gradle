'use strict';

import * as vscode from 'vscode'

export class GradleService {

    projectDir: string
    extensionDir: string
    jvmcode: any
    tasks: string[]
    dependencies: any[]
    classpath: any[]

    constructor(projectDir: string, extensionDir: string, jvmcode: any) {
        this.projectDir = projectDir
        this.extensionDir = extensionDir
        this.jvmcode = jvmcode
    }

    public async connect(progress: vscode.Progress<{message?: string}>) : Promise<any> {
        progress.report({message: 'Gradle: Connecting to '+this.projectDir})
        let reply = await this.jvmcode.send('gradle.connect', { projectDir: this.projectDir, extensionDir: this.extensionDir })
        this.tasks = reply.body.tasks
        this.dependencies = reply.body.dependencies
        this.classpath = reply.body.classpath
        return reply.body
    }

    public async refresh(progress: vscode.Progress<{message?: string}>) : Promise<any> {
        progress.report({message: 'Gradle: Refreshing '+this.projectDir})
        let reply = await this.jvmcode.send('gradle.refresh', { })
        this.tasks = reply.body.tasks
        this.dependencies = reply.body.dependencies
        this.classpath = reply.body.classpath
        return reply.body
    }

    public async runTask(task: string, progress: vscode.Progress<{message?: string}>) {
        progress.report({message: 'Starting '+task})
        let reply = await this.jvmcode.send('gradle.run-task', { task: task })
        return reply.body
    }
}