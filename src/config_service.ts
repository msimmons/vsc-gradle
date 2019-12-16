import * as vscode from 'vscode'

export class ConfigService {

    static refreshGlob() : string {
        return vscode.workspace.getConfiguration('vsc-gradle').get('refreshGlob')
    }

    static isAutorefresh() : boolean {
        return vscode.workspace.getConfiguration('vsc-gradle').get('autorefresh')
    }
}