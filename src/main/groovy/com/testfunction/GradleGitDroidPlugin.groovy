package com.testfunction

import com.testfunction.extensions.dependency.GitDependencyExt
import com.testfunction.extensions.project.GitProjectExt
import com.testfunction.extensions.project.GitProjectTestExt
import com.testfunction.internal.helpers.GitDependenciesHelper
import com.testfunction.internal.utils.Utils
import com.testfunction.tasks.GitDependenciesTask
import com.testfunction.tasks.GitDependencyGetTask
import com.testfunction.tasks.ListDependenciesTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

/**
 * Gradle plugin to create tasks for git dependencies. If automaticResolve is true then unresolved or git dependencies
 * with errors will be cloned/pulled, and compiled automatically.
 *
 * Created by Brandon on 1/1/2016.
 */
class GradleGitDroidPlugin implements Plugin<Project> {
    final Logger log = Logging.getLogger GradleGitDroidPlugin

    @Override
    void apply(Project target) {
        log.debug("GradleGitDroidPlugin init")
        def gitProjectExt = target.extensions.create(GitProjectExt.NAME, GitProjectExt, target);
        def testConf = ((GitProjectTestExt) gitProjectExt.test)

        log.debug("Extensions:$target.gitDroid");

        def gitDependenciesTask = target.tasks.create("initGitDependencies", GitDependenciesTask)
        def gitDependenciesGetTask = target.tasks.create("getGitRepo", GitDependencyGetTask)
        gitDependenciesGetTask.mustRunAfter(gitDependenciesTask)

        def listGitDependenciesTask = target.tasks.create("listGitDependencies", ListDependenciesTask)

        target.gradle.afterProject {
            log.debug "afterProject testSkip"

            log.lifecycle("gitProjectExt.workingDir:$gitProjectExt.workingDir")
            Utils.validateWorkingDirPathLength(target, gitProjectExt.workingDir, gitProjectExt.test.pathLengthDieOnFailure)
            log.debug "test tasks to run:$target.gradle.startParameter.taskNames"
            target.gradle.startParameter.taskNames.each { String taskName ->
                println "task to run:$taskName"
                if (taskName.equals("initGitDependencies")) {
                    gitProjectExt.automaticResolve = true
                }
            }
            target.tasks.findByName("build").shouldRunAfter(gitDependenciesTask)
        }

        target.afterEvaluate {
            def shouldInitGit = false
            def buildError = false
            def Set<Configuration> confsList
            def buildList = []
            if (testConf.filterConfig) {
                log.debug("getting configurations that are unresolved or with errors")
                confsList = target.configurations.findAll { it.state != Configuration.State.RESOLVED }
            } else {
                log.debug("getting configurations")
                confsList = target.configurations.findAll()
            }
            confsList.each { Configuration configuration ->
                log.debug("configuration:${configuration.name} state is ${configuration.state.name()}")
                def Configuration configCopy = configuration.copy()
                if (configCopy.resolvedConfiguration.hasError()) {
                    buildError = true
                }

                def Set<Dependency> depsList
                if (testConf.filterDependenciesByExternalModule) {
                    log.debug("getting dependencies that are external module only")
                    depsList = configuration.dependencies.findAll { it instanceof ExternalModuleDependency }
                } else {
                    log.debug("getting all dependencies")
                    depsList = configuration.dependencies.findAll()
                }
                depsList.each { Dependency dependency ->
                    log.debug("configuration:$configuration.name dependency name:${dependency.name}")
                    if (dependency instanceof ExternalModuleDependency) {
                        def ExternalModuleDependency dep = (ExternalModuleDependency) dependency

                        if (dep.hasProperty("git")) {
                            GitDependencyExt gitExt = dep['git']
                            log.debug("hasproperty dependency:" + dep + " has git:" + dep['git']);
                            if (configCopy.resolvedConfiguration.hasError()) {
                                log.debug("configCopy of $configuration.name hasError:${configCopy.resolvedConfiguration.hasError()}")
                                configCopy.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.each { UnresolvedDependency unDep ->
                                    log.debug("unresolved dependency:${unDep.problem?.message} caused by ${unDep.problem?.cause?.message}")
                                    if("$unDep.selector.group:$unDep.selector.name:$unDep.selector.version"
                                            .equals("$dep.group:$dep.name:$dep.version")) {
                                        log.debug("dependency with git cause of configuration error. adding shouldInitGit")
                                        shouldInitGit = true
                                        buildList.add("$dep.group:$dep.name:$dep.version")
                                    }
                                }
                            }
                            try {
                                if (gitExt.keepUpdated || gitExt.forceBuild) {
                                    shouldInitGit = true
                                    if (!buildList.contains("$dep.group:$dep.name:$dep.version")) {
                                        buildList.add("$dep.group:$dep.name:$dep.version")
                                    }
                                }
                            } catch (e) {
                                log.warn("could not set shouldInitGit")
                                e.printStackTrace()
                            }

                            log.debug("forceRunInit:$testConf.forceRunInit")
                        } else {
                            log.debug("NOT GIT:$dep")
                        }

                    } else {
                        log.debug("not external module dependency")
                    }
                }
            }

            log.debug("shouldInitGit:$shouldInitGit")
            if (shouldInitGit) {
                log.debug("buildError:$buildError && testConf.forceRunInit:$testConf.forceRunInit || gitProjectExt.automaticResolve:$gitProjectExt.automaticResolve")
                if (buildError && testConf.forceRunInit || gitProjectExt.automaticResolve) {
                    try {
                        GitDependenciesHelper.initGitDependencies(target)
                    } catch (e) {
                        log.error("could not run initGitDependencies")
                        e.printStackTrace()
                    }
                } else if(buildError) {
                    log.error("gitDroid found git modules $buildList that have not been initialized. Run initGitDependencies or set gitDroid.automaticResolve to true in project build.gradle to automatically run.")
                } else {
                    log.lifecycle("gitDroid found git modules $buildList with keepUpdated set to true. Run initGitDependencies or set gitDroid.automaticResolve to true in project build.gradle to automatically run.")
                }

            }

        }
    }
}
