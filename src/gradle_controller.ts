'use strict';

import * as vscode from 'vscode'
import { ConnectResult, ConnectRequest } from 'server-models'
import { GradleService } from './gradle_service';
import { ConfigService } from './config_service'
import { ProgressLocation, QuickInputButtons } from 'vscode';
import { readFile } from 'fs';
import * as crypto from 'crypto'

export class GradleController {

    config: any
    projectDir: string
    extensionDir: string
    service: GradleService
    result: ConnectResult
    watcher: vscode.FileSystemWatcher
    problems: vscode.DiagnosticCollection
    refreshLock: Promise<boolean>
    fileHashes: Map<string, string> = new Map<string, string>()

    triggerRefresh = async (uri: vscode.Uri) => {
        if (ConfigService.isAutorefresh() && uri.path.includes('gradle') && uri.scheme === 'file') {
            await this.refresh(uri.path)
        }
    }

    constructor(projectDir: string, extensionDir: string, service: GradleService) {
        this.projectDir = projectDir
        this.extensionDir = extensionDir
        this.service = service
        let rootPath = vscode.workspace.workspaceFolders[0].uri.path
        let pattern = `${rootPath}/${ConfigService.refreshGlob()}`
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
        if (this.refreshLock) await this.refreshLock
        this.refreshLock = new Promise<boolean>(async (resolve, reject) => {
            let shouldRefresh = await this.shouldRefresh(triggerUri)
            if (!shouldRefresh) {
                resolve(true)
            }
            else {
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
                        resolve(true)
                    }
                })
            }
        })
    }

    private async shouldRefresh(path?: string) : Promise<boolean> {
        let hasher = crypto.createHash('md5')
        return new Promise<boolean>((resolve, reject) => {
            if (!path) resolve(true)
            readFile(path, (err, data) => {
                if (err) resolve(false)
                else {
                    hasher.update(data)
                    let hash = hasher.digest('base64')
                    if (this.fileHashes.get(path) === hash) {
                        this.fileHashes.set(path, hash)
                        resolve(false)
                    }
                    else {
                        this.fileHashes.set(path, hash)
                        resolve(true)
                    }
                }
            })
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

    async chooseGradleTasks(currentTasks? : string) : Promise<string> {
        let confirmTasks = vscode.window.createInputBox()
        let taskString = await this.chooseGradleTask(currentTasks)
        confirmTasks.value = taskString
        confirmTasks.title = "Gradle Tasks"
        confirmTasks.prompt = "Enter 'Space' to Choose Another"
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