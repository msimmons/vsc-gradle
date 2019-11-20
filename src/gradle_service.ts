'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest } from 'server-models'

export class GradleService {

    projectDir: string
    extensionDir: string
    jvmcode: any
    result: ConnectResult

    constructor(projectDir: string, extensionDir: string, jvmcode: any) {
        this.projectDir = projectDir
        this.extensionDir = extensionDir
        this.jvmcode = jvmcode
    }

    public async connect(progress: vscode.Progress<{message?: string}>) : Promise<ConnectResult> {
        progress.report({message: 'Gradle: Connecting to '+this.projectDir})
        let request = { projectDir: this.projectDir, extensionDir: this.extensionDir } as ConnectRequest
        let reply = await this.jvmcode.send('gradle.connect', request)
        this.result = reply.body as ConnectResult
        return this.result
    }

    public async refresh(progress: vscode.Progress<{message?: string}>) : Promise<any> {
        progress.report({message: 'Gradle: Refreshing '+this.projectDir})
        let reply = await this.jvmcode.send('gradle.refresh', { })
        this.result = reply.body as ConnectResult
        return this.result
    }

    public async runTask(task: string, progress: vscode.Progress<{message?: string}>) {
        progress.report({message: 'Starting '+task})
        let reply = await this.jvmcode.send('gradle.run-task', { task: task })
        return reply.body
    }
}