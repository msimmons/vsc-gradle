'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest, PluginDiagnostic, ErrorOutput } from './model'
import * as path from 'path'
import {spawn} from 'child_process'
import * as fs from 'fs'
import { promisify } from 'util'

export class GradleService {

    private WHERE_MATCH = /[^']*'(.*)'\s+line:\s+([0-9]*)/
    private WHAT_MATCH = />\s+(.*)/
    private WHAT_MORE_MATCH = /^\s+(.*)/

    private async doConnect(request: ConnectRequest) : Promise<ConnectResult> {
        let command = request.command
        let repoDir = path.join(request.extensionDir, 'out/m2/repo')
        let initScript = path.join(request.extensionDir, 'out/m2/init.gradle')
        let task = 'getModel'
        let env = {
            'REPO_DIR': repoDir,
            'PROJECT_FILE': request.outFile
        }
        let args = ['--init-script', initScript, '-q', '--console', 'plain', task]
        let currentError: PluginDiagnostic
        let child = spawn(command, args, {env: env, cwd: request.projectDir})
        child.stderr.on('data', chunk => currentError = this.processError(chunk.toString(), currentError))
        return new Promise((resolve, reject) => {
            child.on('exit', async (code, signal) => {
                let data = await promisify(fs.readFile)(request.outFile)
                let result = JSON.parse(data.toString()) as ConnectResult
                if (currentError) result.errors.push(currentError)
                resolve(result)
            })
        })
    }

    private processError(output: string, current: PluginDiagnostic) : PluginDiagnostic {
        if (output) {
            console.log(output)
        }
        output.split('\n').forEach(line =>{
            let where = this.WHERE_MATCH.exec(line)
            if (where) current = {file: where[1], line: +where[2], message: ''}
            else {
                let what = this.WHAT_MATCH.exec(line)
                if (what && current) current.message = what[1]
                else if (current && current.message) {
                    let more = this.WHAT_MORE_MATCH.exec(line)
                    if (more) {
                        current.message = `${current.message}\n${more[1]}`
                    }
                }
            }
        })
        return current
    }

    public async connect(request: ConnectRequest) : Promise<ConnectResult> {
        return this.doConnect(request)
    }

    public async runTask(task: string, progress: vscode.Progress<{message?: string}>) {
    }
}