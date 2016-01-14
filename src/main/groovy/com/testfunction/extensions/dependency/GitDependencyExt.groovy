package com.testfunction.extensions.dependency

import com.testfunction.internal.enums.GitReferenceType
import org.gradle.api.tasks.StopExecutionException

/**
 * The git extension for an ExternalModuleDependency.
 * Example:
 * <pre>
 * @code
 * compile (group: 'com.testfunction',
 *  name:'lumberjill',
 *  version:'HEAD',
 *  ext: 'aar',
 *  transitive: true
 * ).ext {
 *  git = [
 *      repo : 'https://github.com/lodlock/LumberJill.git',
 *      buildJavaDocs : true,
 *      keepUpdated : true,
 *      remoteModule: 'lumberjill',
 *      keepSource : true
 *  ]
 * }
 * </pre>
 *
 * Created by Brandon on 1/1/2016.
 */
class GitDependencyExt {
    def String repo = "";
    def String ref = ""
    def GitReferenceType refType = GitReferenceType.AUTOMATIC
    def String remoteModule = "";
    def LinkedHashMap credentials = null;
    def boolean keepUpdated = false;
    def boolean keepSource = false;
    def ArrayList remoteModules = null;
    def boolean uploadDependencies = true;

    //todo add buildSources
    def boolean buildSources = false;
    //todo add buildJavaDocs
    def boolean buildJavaDocs = false;

    def boolean forceBuild = false

    def propertyMissing(String name) {
        throw new StopExecutionException("gitDroid does not have property:$name")
    }

    def propertyMissing(String name, def arg) {
        throw new StopExecutionException("gitDroid does not have property:$name with args:$arg")
    }


    def toListString() {
        def out = """\

|-> repo:$repo
|-> ref:$ref
|-> refType:$refType
|-> remoteModule:$remoteModule
|-> remoteModules:$remoteModules
|-> credentials:
    |-> username:${credentials?.username != null}
    |-> password:${credentials?.password != null}
    |-> sshKey:${credentials?.sshKey != null}
|-> keepUpdated:$keepUpdated
|-> keepSource:$keepSource
|-> uploadDependencies:$uploadDependencies
|-> buildSources:$buildSources
|-> buildJavaDocs:$buildJavaDocs
"""
        return out
    }
}
