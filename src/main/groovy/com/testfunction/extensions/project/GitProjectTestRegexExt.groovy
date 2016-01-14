package com.testfunction.extensions.project

import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

/**
 * gitDroid test.regex extension used for setting regex flags
 * Example:
 * <pre>
 * @code
 * gitDroid {
 *  test {
 *      regexp {
 *          flags = "g"
 *          byLine = true
 *          encoding = "UTF-8"
 *      }
 *  }
 * }
 * </pre>
 *
 * Created by Brandon on 1/9/2016.
 */
class GitProjectTestRegexExt {
    //g global, i case insensitive, m multiline, s singleline
    def flags = "g"
    def byLine = false
    def encoding = "UTF-8"


    GitProjectTestRegexExt(Project project) {
    }

    def propertyMissing(String name) {
        throw new StopExecutionException("gitDroid does not have property:$name")
    }

    def propertyMissing(String name, def arg) {
        throw new StopExecutionException("gitDroid does not have property:$name with args:$arg")
    }

}
