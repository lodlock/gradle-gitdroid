package com.testfunction.tasks

import com.testfunction.internal.helpers.GitDependenciesHelper
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task for initializing git dependencies
 *
 * Created by Brandon on 1/3/2016.
 */
class GitDependenciesTask extends DefaultTask {
    private static Logger LOG = Logging.getLogger(GitDependenciesTask)
    @Override
    def String getDescription() {
        "Initialize git-based dependencies."
    }

    @Override
    def String getGroup() {
        "GitDroid"
    }


    @TaskAction
    def initGitDependencies() {
        LOG.debug("initGitDependencies init")
        GitDependenciesHelper.initGitDependencies(project)
    }
}
