package com.testfunction.tasks

import com.testfunction.internal.helpers.GitDependenciesHelper
import org.gradle.api.DefaultTask
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.TaskAction

/**
 * Gradle task to list git dependencies grouped by resolved, unresolved, and needsbuild
 *
 * Created by Brandon on 1/3/2016.
 */
class ListDependenciesTask extends DefaultTask {
    private static Logger LOG = Logging.getLogger(ListDependenciesTask)
    @Override
    def String getDescription() {
        "Create list of git-based dependencies."
    }

    @Override
    def String getGroup() {
        "GitDroid"
    }


    @TaskAction
    def listGitDependencies() {
        LOG.debug("listGitDependencies init")
        GitDependenciesHelper.listGitDependencies(project)
    }
}
