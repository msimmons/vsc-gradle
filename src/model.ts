export interface ConnectRequest {
    command: string
    extensionDir: string
    projectDir: string
    outFile: string
}

export interface ConnectResult extends ProjectUpdateData {
    errors: PluginDiagnostic[]
    tasks: string[];
}

export interface ErrorOutput {
    where: string
    what: string
}

export interface PluginDiagnostic {
    file: string;
    line: number;
    message: string;
}

// The following could/should come from jvmcode

export interface JvmCodeApi {
    updateProject(project: ProjectUpdateData)
}

interface DependencyData {
    artifactId: string;
    fileName: string;
    groupId: string;
    jmod: string | null;
    modules: string[];
    resolved: boolean;
    scopes: string[];
    sourceFileName: string | null;
    transitive: boolean;
    version: string;
}

interface DependencySourceData {
    dependencies: DependencyData[];
    description: string;
    source: string;
}

interface PathData {
    classDir: string;
    module: string;
    name: string;
    source: string;
    sourceDir: string;
}

interface ProjectUpdateData {
    dependencySources: DependencySourceData[];
    paths: PathData[];
    source: string;
}
