'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest } from 'server-models'
import { GradleService } from './gradle_service';
import { ProgressLocation } from 'vscode';

export class GradleController {

    config: any
    projectDir: string
    extensionDir: string
    service: GradleService
    result: ConnectResult
    watcher: vscode.FileSystemWatcher
    problems: vscode.DiagnosticCollection
    refreshing: boolean = false

    triggerRefresh = async (uri: vscode.Uri) => {
        if (this.config.get('autorefresh') && uri.path.includes('gradle')) {
            await this.refresh(uri.path)
        }
    }

    constructor(config: any, projectDir: string, extensionDir: string, service: GradleService) {
        this.config = config
        this.projectDir = projectDir
        this.extensionDir = extensionDir
        this.service = service
        let pattern = vscode.workspace.rootPath+`/${config.get('refreshGlob')}`
        this.watcher = vscode.workspace.createFileSystemWatcher(pattern)
        this.watcher.onDidChange(this.triggerRefresh)
        this.watcher.onDidDelete(this.triggerRefresh)
        this.problems = vscode.languages.createDiagnosticCollection('vsc-gradle')
    }

    public async connect() {
        vscode.window.withProgress({ location: vscode.ProgressLocation.Window, title: 'VSC-Gradle' }, async (progress) => {
            progress.report({message: 'Connecting to '+this.projectDir})
            let request = { projectDir: this.projectDir, extensionDir: this.extensionDir } as ConnectRequest
            this.result = await this.service.connect(request)
            this.setProblems(this.result)
        })
    }

    public async refresh(triggerUri?: string) {
        if (this.refreshing) return
        this.refreshing = true
        vscode.window.withProgress({ location: vscode.ProgressLocation.Window, title: 'VSC-Gradle' }, async (progress) => {
            let message = `Refreshing ${triggerUri ? triggerUri : this.projectDir}`
            progress.report({message: message})
            try {
                let r = await this.service.refresh()
                this.setProblems(r)
                if (r.errors) this.result.errors = r.errors
                else this.result = r
            }
            finally {
                this.refreshing = false
            }
        })
    }

    private setProblems(res: ConnectResult) {
        this.problems.clear()
        if (res.errors.length > 0) vscode.window.showErrorMessage("There were errors connecting to gradle project")
        res.errors.forEach((e) => {
            let uri = e.file ? vscode.Uri.file(e.file) : vscode.Uri.file(this.projectDir)
            let existing = this.problems.get(uri)
            let range = e.line ? new vscode.Range(e.line-1, 0, e.line-1, 0) : new vscode.Range(0, 0, 0, 0)
            let diag = new vscode.Diagnostic(range, e.message, vscode.DiagnosticSeverity.Error)
            this.problems.set(uri, existing.concat(diag))
        })
    }

    public async runTask() {
        vscode.window.showQuickPick(this.result.tasks, {canPickMany: false}).then((choice) => {
            // TODO Allow mutliple task choices
            if (!choice ) return
            // TODO Actually show progress
            vscode.window.withProgress({ location: ProgressLocation.Window, title: choice[0] }, (progress) => {
                progress.report({message: `Starting ${choice}`})
                let reply = this.service.runTask(choice[0], progress).catch((reason) => {
                    vscode.window.showErrorMessage('Error running task: ' + reason.message)
                })
                return reply
            })
        })
    }
}