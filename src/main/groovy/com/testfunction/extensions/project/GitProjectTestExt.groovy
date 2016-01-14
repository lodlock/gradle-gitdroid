package com.testfunction.extensions.project

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.StopExecutionException

/**
 * gitDroid test extension used for setting test values
 * Example:
 * <pre>
 * @code
 * gitDroid {
 *  test {
 *      pathLengthDieOnFailure = true
 *  }
 * }
 * </pre>
 *
 * Created by Brandon on 1/6/2016.
 */
class GitProjectTestExt {
    def boolean forceRunInit = false
    def boolean filterConfig = true
    def boolean filterDependenciesByExternalModule = true
    def boolean pathLengthDieOnFailure = true
    def boolean useLifecycle = false
    def GitProjectTestRegexExt regexp
    private Project target

    GitProjectTestExt(Project target) {
        regexp = ((ExtensionAware) this).extensions.create("regexp", GitProjectTestRegexExt, target)
        this.target = target
    }

    def propertyMissing(String name) {
        if (!target.properties.containsKey(name)) {
            throw new StopExecutionException("gitDroid does not have property:$name")
        }
    }

    def propertyMissing(String name, def arg) {
        if (!target.properties.containsKey(name)) {
            throw new StopExecutionException("gitDroid does not have property:$name with args:$arg")
        }
    }

}
