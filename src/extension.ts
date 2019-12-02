'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { ProgressLocation } from 'vscode'
import { GradleService } from './gradle_service'
import { GradleController } from './gradle_controller';

let jvmcode: any
let gradleService: GradleService
let gradleController: GradleController
let config: any

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

    jvmcode = vscode.extensions.getExtension('contrapt.jvmcode').exports
    config = vscode.workspace.getConfiguration("vsc-gradle")

    installVerticle()

    function installVerticle() {
        let jarFile = context.asAbsolutePath('out/vsc-gradle.jar')
        let config = vscode.workspace.getConfiguration("vsc-gradle")
        let jarFiles = [jarFile]
        jvmcode.install(jarFiles, 'net.contrapt.gradle.GradleVerticle').then((result) => {
            registerProviders()
            connectGradle()
        }).catch((error) => {
            vscode.window.showErrorMessage('Error starting gradle service: ' + error.message)
        })
    }

    function registerProviders() {
        // Do we have a task provider??
    }

    function connectGradle() {
        gradleService = new GradleService(jvmcode)
        gradleController = new GradleController(config,vscode.workspace.rootPath, context.extensionPath, gradleService)
        gradleController.connect()
    }

    context.subscriptions.push(vscode.commands.registerCommand('gradle.refresh', () => {
        gradleController.refresh()
    }))

    context.subscriptions.push(vscode.commands.registerCommand('gradle.run-task', () => {
        vscode.window.showQuickPick(gradleController.result.tasks, {}).then((choice) => {
            // TODO Allow mutliple task choices
            if (!choice ) return
            // TODO Actually show progress
            vscode.window.withProgress({ location: ProgressLocation.Window, title: choice }, (progress) => {
                return gradleController.runTask(choice, progress).catch((error) => {
                    vscode.window.showErrorMessage('Error running task: ' + error.message)
                })
            })
        })
    }))
}

// this method is called when your extension is deactivated
export function deactivate() {
    console.log('Closing all the things')
}   