# VSC-Gradle

This extension provides Gradle related services for any project that uses gradle as a build tool.
We use the extension _contrapt.jvmcode_ to provide a JVM server and event bus on which to run and communicate with
the Gradle services.  It provides insight into the project dependencies and project tasks

## Features

- Connect to a Gradle project build file
- Supplies dependencies and source and class paths to JVM Code
- Supplies available build tasks. 
  - Use the command _gradle.choose-tasks_ in your tasks.json to prompt the user to choose available tasks

## Requirements

This extension requires the _contrapt.jvmcode_ extension to be installed

## Extension Settings

## Known Issues

## Release Notes

### 1.0.0

Initial release of ...
