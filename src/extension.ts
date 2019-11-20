'use strict';
// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { StatusBarItem, Uri, ProgressLocation } from 'vscode'
import { GradleService } from './gradle_service'

let jvmcode
let gradleService: GradleService

// this method is called when your extension is activated
// your extension is activated the very first time the command is executed
export function activate(context: vscode.ExtensionContext) {

    jvmcode = vscode.extensions.getExtension('contrapt.jvmcode').exports

    installVerticle()

    function installVerticle() {
        let jarFile = context.asAbsolutePath('out/vsc-gradle.jar')
        let config = vscode.workspace.getConfiguration("gradle")
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
        gradleService = new GradleService(vscode.workspace.rootPath, context.extensionPath, jvmcode)
        vscode.window.withProgress({ location: ProgressLocation.Window, title: 'Connect to Gradle' }, (progress) => {
            return gradleService.connect(progress).then((reply) => {
                progress.report({message: 'Connected'})
                if (reply.errors) {
                    vscode.window.showErrorMessage(reply.errors.join('\n'))
                }
            })
            .catch((error) => {
                vscode.window.showErrorMessage('Error connecting to gradle: ' + error.message)
            })
        })
    }

    context.subscriptions.push(vscode.commands.registerCommand('gradle.refresh', () => {
        vscode.window.withProgress({ location: ProgressLocation.Window, title: 'Refresh Gradle' }, (progress) => {
            return gradleService.refresh(progress).then((reply) => {
                progress.report({message: 'Refreshed'})
                if (reply.errors) {
                    vscode.window.showErrorMessage(reply.errors.join('\n'))
                }
            })
            .catch((error) => {
                vscode.window.showErrorMessage('Error refreshing gradle: ' + error.message)
            })
        })
    }))

    context.subscriptions.push(vscode.commands.registerCommand('gradle.run-task', () => {
        vscode.window.showQuickPick(gradleService.result.tasks, {}).then((choice) => {
            // TODO Allow mutliple task choices
            if (!choice ) return
            // TODO Actually show progress
            vscode.window.withProgress({ location: ProgressLocation.Window, title: choice }, (progress) => {
                return gradleService.runTask(choice, progress).catch((error) => {
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