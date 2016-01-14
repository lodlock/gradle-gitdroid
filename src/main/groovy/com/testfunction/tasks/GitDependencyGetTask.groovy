package com.testfunction.tasks

import com.testfunction.internal.helpers.GitDependenciesHelper
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task for clone/pull of git dependency repositories
 *
 * Created by Brandon on 1/6/2016.
 */
class GitDependencyGetTask extends DefaultTask {
    def static Logger LOG = Logging.getLogger(GitDependenciesTask)

    @Override
    def String getDescription() {
        "Get projects via a git clone or pull"
    }

    @Override
    def String getGroup() {
        "GitDroid"
    }

    @TaskAction
    def getGitRepo() {
        LOG.debug("getGitRepo init")
        GitDependenciesHelper.getGitRepo(project)
    }
}
