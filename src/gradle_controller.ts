'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest } from 'server-models'
import { GradleService } from './gradle_service';
import { ConfigService } from './config_service'
import { ProgressLocation, QuickInputButtons } from 'vscode';

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
        if (ConfigService.isAutorefresh() && uri.path.includes('gradle')) {
            await this.refresh(uri.path)
        }
    }

    constructor(projectDir: string, extensionDir: string, service: GradleService) {
        this.projectDir = projectDir
        this.extensionDir = extensionDir
        this.service = service
        let pattern = vscode.workspace.rootPath+`/${ConfigService.refreshGlob()}`
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

    private async chooseGradleTasks(currentTasks? : string) : Promise<string> {
        let confirmTasks = vscode.window.createInputBox()
        let taskString = await this.chooseGradleTask(currentTasks)
        confirmTasks.value = taskString
        confirmTasks.show()
        confirmTasks.onDidChangeValue(async (event) => {
            if (event === ' ') {
                confirmTasks.hide()
                taskString = await this.chooseGradleTask(taskString)
                confirmTasks.value = taskString
                confirmTasks.show()
            }
        })
        let result = new Promise<string>((resolve, reject) => {
            confirmTasks.onDidAccept(event => {
                let choice = confirmTasks.value
                if (choice) resolve(choice)
                confirmTasks.dispose()
            })
        })
        return result
    }

    private async chooseGradleTask(currentTasks? : string) : Promise<string> {
        let quickPick = vscode.window.createQuickPick()
        quickPick.matchOnDescription = true
        quickPick.buttons = [ {iconPath: vscode.ThemeIcon.File, tooltip: "pick it"} as vscode.QuickInputButton ]
        let taskItems = this.result.tasks.filter(t => {
            if (!currentTasks) return true
            else return !currentTasks.split(' ').includes(t)
        })
        .map(t => {
            let label = currentTasks ? `${currentTasks} ${t}` : t
            return { label: label } as vscode.QuickPickItem
        })
        quickPick.value = currentTasks ? `${currentTasks} ` : ''
        quickPick.items = taskItems
        let result = new Promise<string>((resolve, reject) => {
            quickPick.onDidAccept(selection => {
                quickPick.dispose()
                if (quickPick.selectedItems.length) {
                    resolve(quickPick.selectedItems[0].label)
                }
                else {
                    resolve(undefined)
                }
            })

        })
        quickPick.show()
        return result
    }

    public async runTask() {
        let tasks = await this.chooseGradleTasks()
        this.runGradleTask(tasks)
    }

    private async runGradleTask(tasks: string) {
        vscode.workspace.registerTaskProvider
        let def = {type: 'vsc-code'} as vscode.TaskDefinition
        const writeEmitter = new vscode.EventEmitter<string>();
        const closeEmitter = new vscode.EventEmitter<any>();
        const pty: vscode.Pseudoterminal = {
            onDidWrite: writeEmitter.event,
            onDidClose: closeEmitter.event,
            open: () => {
                setTimeout(() => { closeEmitter.fire() }, 1000)
            },
            close: () => { console.log('Closed')}
        };
        let exec = new vscode.CustomExecution(async () => {
            return pty
        })
        let task = new vscode.Task(def, vscode.workspace.workspaceFolders[0], tasks, 'vsc-gradle', exec, [])
        vscode.tasks.executeTask(task)

        // TODO Actually show progress
        vscode.window.withProgress({ location: ProgressLocation.Window, title: tasks }, (progress) => {
            progress.report({message: `Starting ${tasks}`})
            let reply = this.service.runTask(tasks, progress).catch((reason) => {
                vscode.window.showErrorMessage('Error running task: ' + reason.message)
            })
            return reply
        })
    }

}