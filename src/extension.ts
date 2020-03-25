'use strict';

import * as vscode from 'vscode';
import { GradleController } from './gradle_controller';

let gradleController: GradleController

export function activate(context: vscode.ExtensionContext) {

    gradleController = new GradleController(context)
    gradleController.connect()

    context.subscriptions.push(vscode.commands.registerCommand('gradle.refresh', () => {
        gradleController.refresh()
    }))

    context.subscriptions.push(vscode.commands.registerCommand('gradle.run-task', () => {
        gradleController.runTask()
    }))

    context.subscriptions.push(vscode.commands.registerCommand('gradle.choose-tasks', async () => {
        let tasks = await gradleController.chooseGradleTasks()
        if (tasks) return tasks
        else return ""
    }))
}

export function deactivate() {
    console.log('Closing all the things')
}   