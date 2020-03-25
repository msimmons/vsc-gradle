import * as vscode from 'vscode'

export class ConfigService {
    static namespace = 'vsc-gradle'

    static refreshGlob() : string {
        return vscode.workspace.getConfiguration(this.namespace).get('refreshGlob')
    }

    static isAutorefresh() : boolean {
        return vscode.workspace.getConfiguration(this.namespace).get('autorefresh')
    }

    static command() : string {
        return vscode.workspace.getConfiguration(this.namespace).get('command')
    }
}