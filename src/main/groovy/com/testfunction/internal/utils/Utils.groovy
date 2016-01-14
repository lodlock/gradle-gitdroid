package com.testfunction.internal.utils

import org.gradle.api.Project
import org.gradle.api.tasks.StopExecutionException

import static com.android.SdkConstants.PLATFORM_WINDOWS
import static com.android.SdkConstants.currentPlatform

/**
 * Utils
 * Basic file checks such as checking that a build file is an android library, contains gitDroid, or isn't too
 * long for windows file systems.
 *
 * Created by Brandon on 1/14/2016.
 */
class Utils {
    /**
     * Check that file contains "com.android.library"
     *
     * @param project the main project
     * @param file the file to check
     * @return true if is library, false if not
     */
    static def isAndroidLibrary(Project project, File file) {
        def log = project.logger
        log.debug("isAndroidLibrary init checking file:"+file.absolutePath)
        if (!file.exists()) {
            log.warn("file does not exist")
        }
        return (file.exists()) ? file.getText('UTF-8').contains("com.android.library") : false
    }

    /**
     * Check that file contains "gitDroid.gradle"
     *
     * @param project the main project
     * @param file the file to check
     * @return true if contain gitDroid.gradle, false if not
     */
    def static boolean containsGitDroid(Project project, File file) {
        def log = project.logger
        log.debug("containsGitDroid init with file:"+file.absolutePath)
        if (!file.exists()) {
            log.warn("file does not exist")
        }
        return (file.exists()) ? file.getText('UTF-8').contains("gitDroid.gradle") : false

    }

    /**
     * Check if project's gitDroid.workingDir is too long for a Windows platform.
     * It is common for android compile dependency libraries to have a length > 160
     * characters. Due to this we need to ensure that the workingDir path is <= 64
     * characters in length. This only applies to Windows
     *
     * @param project target project used for log
     * @param workingDir the directory to test
     * @param dieOnFailure true if should throw exception on failure false to log
     * @return
     * @throws org.gradle.api.tasks.StopExecutionException if workingDir length > 65 and dieOnFailure is true
     */
    @SuppressWarnings("GroovyLocalVariableNamingConvention")
    static def validateWorkingDirPathLength(Project project, File workingDir, boolean dieOnFailure)
            throws StopExecutionException {
        def log = project.logger
        log.debug("validateWorkingDirPathLength init with workingDir:"+workingDir.absolutePath)
        if (currentPlatform() == PLATFORM_WINDOWS) {
            log.debug("platform is windows")
            if (workingDir.absolutePath.toCharArray().length > 65) {
                def reason = "Unlikely to build git projects with current long gitDroid workingDir as Windows path length limit will likely be reached. Change gitDroid workingDir to shorter path. e.g., 'C:\\tmp' length: 5 vs current workingDir:'$workingDir.absolutePath' length:${workingDir.absolutePath.length()}"
                if (dieOnFailure) {
                    throw new StopExecutionException(reason)
                } else {
                    log.warn(reason)
                }
            }

        } else {
            log.debug("not a windows platform, ignoring path length")
        }

    }
}
