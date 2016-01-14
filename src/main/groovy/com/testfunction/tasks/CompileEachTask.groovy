package com.testfunction.tasks

import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.TaskAction

/**
 * This task should never be visible by the user. It is created on the fly and deleted when done.
 *
 * Created by Brandon on 1/6/2016.
 */
class CompileEachTask extends GradleBuild {
    def run = false

    @Override
    def String getDescription() {
        "Cannot be run directly. Used as a template task to create individual build tasks for each git project."
    }

    @Override
    def String getGroup() {
        "GitDroid"
    }

    @TaskAction
    def compileEach() {
        def log = project.logger
        log.debug("compileEach init")
        if (!run) {
            throw new StopActionException("Cannot run compileEach directly")
        }
    }
}
