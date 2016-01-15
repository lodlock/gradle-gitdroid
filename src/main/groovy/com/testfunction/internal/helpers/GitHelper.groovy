package com.testfunction.internal.helpers

import com.testfunction.extensions.dependency.GitDependencyExt
import org.ajoberstar.grgit.Grgit
import org.ajoberstar.grgit.Repository
import org.ajoberstar.grgit.auth.AuthConfig
import org.ajoberstar.grgit.exception.GrgitException
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopActionException

/**
 * GitHelper
 * Git calls are all made through this class and passed to Grgit or jgit depending on need.
 *
 * Created by Brandon on 1/6/2016.
 */
class GitHelper {
    /**
     * Clone the dependency git repository provided by gitExt.
     *
     * @param project the main project
     * @param dependency the git dependency
     * @param gitExt the git extension of the dependency
     * @param destinationDir the temp directory to clone into
     * @return
     */
    def static boolean cloneGitRepository(
            Project project,
            ExternalModuleDependency dependency,
            GitDependencyExt gitExt,
            File destinationDir) {

        def log = project.logger;
        log.debug("init cloneGitRepository")

        def build = true

        def grgit
        if (gitExt.ref) {
            grgit = Grgit.clone(dir: destinationDir.absolutePath, uri: gitExt.repo, checkout: true, refToCheckout: gitExt.ref)
        } else {
            grgit = Grgit.clone(dir: destinationDir.absolutePath, uri: gitExt.repo)
        }

        log.debug("clone command created checking for branch $gitExt.ref $gitExt.refType")
        log.debug("calling git")

        closeGit(log, grgit)
        build = testRepo(log, destinationDir)
        return build
    }

    /**
     * Close the git repository
     *
     * @param log the logger to be used
     * @param git current instance of Grgit
     * @return
     */
    static def closeGit(Logger log, Grgit git) {
        log.debug("closing repository")
        git.repository.jgit.repository.close()
        git.repository.jgit.close()
        git.close()
    }

    /**
     * Use existing repository and call a pull command.
     *
     * @param project the main project
     * @param dependency the git dependency
     * @param gitExt the git extension for the dependency
     * @param destinationDir the directory containing the git repository
     * @return
     * @throws IOException
     * @throws GrgitException
     */
    def static boolean pullGitRepository(Project project,
                                         ExternalModuleDependency dependency,
                                         GitDependencyExt gitExt,
                                         File destinationDir) throws IOException, GrgitException {
        def log = project.logger
        log.debug("pullGitRepository init with dependency:$dependency gitExt:$gitExt dependencyDir:$destinationDir")
        def build = true
        def grgit = Grgit.open(dir:destinationDir.absolutePath)

        try {
            if (hasAtLeastOneReference(log, grgit)) {
                log.debug("has at least one ref")
                Repository repo = grgit.repository
                log.debug("!gitExt.branch:$gitExt.refType && repo.branch != master : $repo.jgit.repository.branch")

                if (repo.jgit.repository.branch != "master") {
                    log.debug("checkout master")
                    repo.jgit.stashCreate().call()
                    def cmd = repo.jgit.checkout()
                    cmd.name = "origin/master"
                    cmd.force = true
                    cmd.call()
                }

                log.debug("git pull")
                handleGitCreds(log, gitExt, grgit)
                grgit.fetch(prune: true)

                grgit.branch.remove(names: ['gitDroid-branch'], force: true)
                if (gitExt.ref) {
                    grgit.checkout(branch: "gitDroid-branch", startPoint: gitExt.ref, createBranch: true)
                } else {
                    grgit.checkout(branch: "gitDroid-branch", createBranch: true)
                }
            } else {
                log.warn("does not have any refs")
                closeGit(log, grgit)
                throw new StopActionException("not a valid local git repository")
            }
            closeGit(log, grgit)
        } catch (e) {
            e.printStackTrace()
            if (grgit != null) {
                closeGit(log, grgit)
            }
            throw new StopActionException("Error pulling repository")
        }

        build = testRepo(log, destinationDir)
        return build
    }

    /**
     * Sets credentials for use with Grgit instance to clone/pull from a private repo
     *
     * @param log the logger to use
     * @param gitExt the git extension of the dependency
     * @param grgit the current instance of Grgit
     * @return
     */
    def static handleGitCreds(Logger log, GitDependencyExt gitExt, Grgit grgit) {
        clearOriginalCreds()
        if (gitExt.credentials != null && !gitExt.credentials.isEmpty()) {
            log.debug("credentials found")

            def username = (String) gitExt.credentials["username"]
            if (username) {
                System.properties[AuthConfig.USERNAME_OPTION] = username
            }
            def password = (String) gitExt.credentials["password"]
            if (password) {
                log.debug("password found")
                System.properties[AuthConfig.PASSWORD_OPTION] = password
            } else {
                log.warn("credentials missing username or password")
            }

            def sshKey = (String) gitExt.credentials["sshKey"]
            if (sshKey) {
                System.properties[AuthConfig.SSH_PRIVATE_KEY_OPTION] = sshKey
            }
        }
        return true
    }

    /**
     * Removes current credentials if they exists from a prior Grgit call
     *
     * @return
     */
    static def clearOriginalCreds() {
        if (System.properties.hasProperty(AuthConfig.USERNAME_OPTION)) {
            System.properties.remove(AuthConfig.USERNAME_OPTION)
        }
        if (System.properties.hasProperty(AuthConfig.PASSWORD_OPTION)) {
            System.properties.remove(AuthConfig.PASSWORD_OPTION)
        }
        if (System.properties.hasProperty(AuthConfig.SSH_PRIVATE_KEY_OPTION)) {
            System.properties.remove(AuthConfig.SSH_PRIVATE_KEY_OPTION)
        }
        return true
    }

    /**
     * Test that the repository in the destinationDir is valid
     *
     * @param log the logger to use
     * @param destinationDir the temp directory that contains the git repo
     * @return true if valid repo is found, false if not
     */
    def static boolean testRepo(Logger log, File destinationDir) {
        log.debug("testing repo")
        def ret
        def Grgit grgit = null
        try {
            grgit = Grgit.open(dir:destinationDir.absolutePath)
            log.debug("grgit:"+grgit)
            if (hasAtLeastOneReference(log, grgit)) {
                log.debug("has at least one reference")
                closeGit(log, grgit)
                ret = true
            } else {
                log.warn("does not have any references")
                closeGit(log, grgit)
                ret = false
            }
        } catch (e) {
            ret = false
            log.warn("git failure:$e.message")
            e.printStackTrace()
            if (grgit != null) {
                closeGit(log, grgit)
            }
        }

        log.debug("done testing repo")
        return ret
    }

    /**
     * Validate that the repository passed contains at least one reference
     *
     * @param log the logger to use
     * @param grgit the current instance of Grgit
     * @return
     */
    def static boolean hasAtLeastOneReference(Logger log, Grgit grgit) {
        def hasRef = grgit.repository.jgit.repository.allRefs.find { key, ref -> ref.objectId != null }
        log.debug "hasRef:" + hasRef
        return hasRef != null
    }
}