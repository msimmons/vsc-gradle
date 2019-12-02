'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest } from 'server-models'

export class GradleService {

    jvmcode: any

    constructor(jvmcode: any) {
        this.jvmcode = jvmcode
    }

    public async connect(request: ConnectRequest) : Promise<ConnectResult> {
        let reply = await this.jvmcode.send('gradle.connect', request)
        return reply.body as ConnectResult
    }

    public async refresh() : Promise<ConnectResult> {
        let reply = await this.jvmcode.send('gradle.refresh', { })
        return reply.body as ConnectResult
    }

    public async runTask(task: string, progress: vscode.Progress<{message?: string}>) {
        progress.report({message: 'Starting '+task})
        let reply = await this.jvmcode.send('gradle.run-task', { task: task })
        return reply.body
    }
}