package com.testfunction.extensions.project

import com.testfunction.extensions.dependency.GitDependencyExt
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.StopActionException
import org.gradle.api.tasks.StopExecutionException

/**
 * The project extension used by adding gitDroid to the build.gradle file.
 * Example:
 * <pre>
 * @code
 * gitDroid {
 *  workingDir = "C:/tmp/${rootProject.name}/gitDroid"
 *  shouldCompile = true
 *  shouldClone = true
 *  shouldBuildArchivePom = true
 *  useLocalProperties = true
 *  cleanIncludesGitSources = false
 *  localRepo = getRepositories().mavenLocal().url
 *  automaticResolve = false
 * }
 * </pre>
 *
 * Created by Brandon on 1/1/2016.
 */
class GitProjectExt {
    public static final String NAME = "gitDroid";
    def File workingDir;
    def boolean shouldBuildArchivePom = true;
    def URI localRepo;
    def boolean automaticResolve = false;
    def GitProjectTestExt test
    def Map<ExternalModuleDependency, GitDependencyExt> needsbuild
    def boolean deleteSourceOnGitFailure = true
    def Map<String, ArrayList<String>> replaceValues = null
    def Map<String, String> replaceValuesRegex = null
    def boolean buildModule = true
    private Project target


    GitProjectExt(Project target) {
        workingDir = target.rootProject.file("git-tmp")
        localRepo = target.repositories.mavenLocal().url;
        test = ((ExtensionAware) this).extensions.create("test", GitProjectTestExt, target)
        this.target = target
    }

    void setWorkingDir(File workingDir) {
        this.workingDir = workingDir
    }

    void setWorkingDir(String workingDir) {
        this.workingDir = new File(workingDir)
    }
}
