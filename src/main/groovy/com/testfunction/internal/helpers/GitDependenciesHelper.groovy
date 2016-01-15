package com.testfunction.internal.helpers

import com.testfunction.extensions.dependency.GitDependencyExt
import com.testfunction.extensions.project.GitProjectExt
import com.testfunction.internal.dependencies.DepNeedsBuild
import com.testfunction.internal.dependencies.DepResolved
import com.testfunction.internal.dependencies.DepUnresolved
import com.testfunction.internal.enums.GitDependencyTypes
import com.testfunction.internal.utils.Utils
import com.testfunction.tasks.CompileEachTask
import org.eclipse.jgit.internal.storage.file.WindowCache
import org.eclipse.jgit.storage.file.WindowCacheConfig
import org.gradle.StartParameter
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.UnresolvedDependency
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.StopActionException

import java.nio.file.Files
import java.nio.file.Path

/**
 * GitDependenciesHelper
 *
 * Get list of ExternalModuleDependency dependencies which have a git extension. If need be start
 * the clone/pull process, a build of the external project with dependencies, and addition to the
 * local defined repo along with dependencies required to run external module.
 *
 * Created by Brandon on 1/3/2016.
 */
class GitDependenciesHelper {

    /**
     * Get a list of dependencies which have git extension based on the resolved state of the dependency
     *
     * @param project the project which contains the dependencies
     * @param type the type to sort by RESOLVED, UNRESOLVED, NEEDSUPDATE
     * @return Map<ExternalModuleDependency, GitDependencyExt> of dependencies which include git extension
     */
    def static getGitDependencies(
            def Project project,
            def GitDependencyTypes type) {
        def log = project.logger
        def Map<ExternalModuleDependency, GitDependencyExt> map = [:]
        log.debug("getGitDependencies init with project:" + project + " map:" + map + " type:" + type)

        log.debug("type:$type")
        project.configurations.findAll().each { Configuration config ->
            Configuration configCopy = config.copy()

            log.debug("configCopy of $config.name")
            def unresolvedList = []
            if (configCopy.resolvedConfiguration.hasError()) {
                log.debug("configCopy of $config.name hasError:${configCopy.resolvedConfiguration.hasError()}")
                configCopy.resolvedConfiguration.lenientConfiguration.unresolvedModuleDependencies.each { UnresolvedDependency unDep ->
                    log.debug("config:$config.name unresolved dependency:${unDep.problem?.message} caused by ${unDep.problem?.cause?.message}")
                    unresolvedList.add("$unDep.selector.group:$unDep.selector.name:$unDep.selector.version")
                }
            }
            log.debug("unresolvedList:" + unresolvedList + " size:" + unresolvedList.size())
            config.allDependencies.each { Dependency dependency ->
                log.debug("type:$type config:$config.name")
                if (!(dependency instanceof ExternalModuleDependency)) {
                    log.debug("dependency:$dependency.name not external module. skipping")
                    return
                }
                def dep = dependency as ExternalModuleDependency
                if (type == GitDependencyTypes.UNRESOLVED && !configCopy.resolvedConfiguration.hasError()) {
                    log.debug("no unresolved dependencies")
                } else {
                    if (dep.hasProperty("git")) {
                        log.debug("$config.name: dependency:$dep.name is of type git:$dep.git")
                        if (unresolvedList.contains("$dep.group:$dep.name:$dep.version")) {
                            log.debug("found dep:$dep.name in unresolvedList")
                            dep['git']['forceBuild'] = true
                            log.debug("unresolvedList adding to map[$dep] = $dep.git")
                            map[(dep)] = (GitDependencyExt) dep['git']
                        }
                        if (configCopy.state == Configuration.State.RESOLVED) {
                            if (type == GitDependencyTypes.NEEDSUPDATE) {

                                if (((GitDependencyExt) dep['git']).keepUpdated) {
                                    log.debug("$config.name: is resolved and keepUpdated is true")
                                    log.debug("resolved keepUpdated adding to map[$dep] = $dep.git")
                                    map[(dep)] = (GitDependencyExt) dep['git']
                                    true
                                } else {
                                    log.debug("$config.name: state is resolved but keepUpdated is false")
                                }
                            } else if (type == GitDependencyTypes.RESOLVED) {
                                log.debug("resolved and type is resolved adding to map[$dep] = $dep.git")
                                map[(dep)] = (GitDependencyExt) dep['git']
                                true
                            }
                        } else {
                            if (((GitDependencyExt) dep['git']).keepUpdated) {
                                log.debug("$config.name: is unresolved and keepUpdated is true")
                                log.debug("unresolved keepUpdated adding to map[$dep] = $dep.git")
                                map[(dep)] = (GitDependencyExt) dep['git']
                                true
                            }
                        }
                    } else {
                        log.debug("$config.name: dependency:$dep.name not a git dependency")
                    }
                }
            }
        }
        log.debug("returning ${map.size()} dependencies")
        return map
    }

    /**
     * Creates repo directories and calls getGitRepo
     *
     * @param project
     */
    static def initGitDependencies(Project project) {
        def log = project.logger
        def gitProjectExt = (GitProjectExt) project.(GitProjectExt.NAME)
        def dir = gitProjectExt.workingDir
        log.debug("dir:$dir")
        def repo = gitProjectExt.localRepo
        def repoFolder = new File(repo)
        if (repoFolder.exists() && repoFolder.directory) {
            log.debug("repo exists and is directory")
        } else {
            log.debug("repo does not exist or is not directory")
            if (repoFolder.mkdirs()) {
                log.debug("repoFolder created:" + repoFolder.absolutePath)
            } else {
                log.debug("failed to create repoFolder:" + repoFolder.absolutePath)
            }
        }
        def dependenciesNeedsBuild = DepNeedsBuild.newInstance()

        dependenciesNeedsBuild.list = getGitDependencies(project, dependenciesNeedsBuild.type)
        log.lifecycle("git dependencies that need to be built:" + dependenciesNeedsBuild.list)

        if (dependenciesNeedsBuild.list.isEmpty()) {
            throw new StopActionException("No dependencies require build")
        }

        log.debug("setting gitDroid.needsbuild for dependencies")
        ((GitProjectExt) project.extensions.findByName("gitDroid")).needsbuild = dependenciesNeedsBuild.list

        log.debug("executing getGitRepo")
        getGitRepo(project)

        log.debug("list of that needs build")
    }

    /**
     * Checks if repo exists and is valid. If repo already exists a git pull command is called, otherwise
     * clone is called.
     *
     * @param project
     * @return
     */
    static def getGitRepo(Project project) {
        def log = project.logger
        def gitDroid = (GitProjectExt) project.extensions.findByName("gitDroid")
        def WindowCacheConfig config = new WindowCacheConfig()
        config.packedGitMMAP = false
        config.install()
        def dependenciesNeedsBuild = gitDroid.needsbuild
        log.debug("getGitRepo needsbuild:$dependenciesNeedsBuild")
        dependenciesNeedsBuild.each { ExternalModuleDependency dependency, GitDependencyExt gitExt ->
            log.debug("getGitRepo - dependenciesNeedsBuild dep:${dependency.name} gitExt:$gitExt")
            if (!gitExt.repo) {
                throw new StopActionException("Missing repo for git")
            }
            def File destinationDir = new File(gitDroid.workingDir, dependency.name)
            def build

            checkRepo(gitDroid, log, destinationDir)


            if (!destinationDir.exists()) {
                log.debug("destinationDir:" + destinationDir + " doesn't exist")
                try {
                    build = GitHelper.cloneGitRepository(project, dependency, gitExt, destinationDir)
                } catch (e) {
                    log.warn("failed to clone for some reason:$e.message")
                    if (gitDroid.deleteSourceOnGitFailure) {
                        log.debug("deleting destination dir")
                        if (destinationDir.deleteDir()) {
                            log.debug("deleted")
                        } else {
                            log.warn("failed to delete $destinationDir.absolutePath")
                        }
                    }
                    build = false
                    e.printStackTrace()
                }
            } else {
                log.debug("destinationDir:" + destinationDir + " exists")
                try {
                    build = GitHelper.pullGitRepository(project, dependency, gitExt, destinationDir)
                    if (gitExt.forceBuild) {
                        log.debug("build was:$build but forceBuild is true")
                        build = true
                    }
                } catch (e) {
                    log.warn("failed to pull for some reason:$e.message")
                    if (gitDroid.deleteSourceOnGitFailure) {
                        log.debug("deleting destination dir")
                        if (destinationDir.deleteDir()) {
                            log.debug("deleted")
                        } else {
                            log.warn("failed to delete $destinationDir.absolutePath")
                        }
                    }
                    build = false
                    e.printStackTrace()
                }
            }
            log.debug("build:$build")
            if (build) {
                handleCompile(project, dependency, gitExt)
            }
        }
    }

    /**
     * Checks if destinationDir exists. If it does tests the repo. If repo fails and gitDroid.deleteSourceOnGitFailure
     * is true then attempts to delete destinationDir.
     *
     * @param gitDroid the project's GitProjectExt value
     * @param log logger to use for logging
     * @param destinationDir the directory to check for a valid repo in
     * @return
     */
    static def checkRepo(GitProjectExt gitDroid, Logger log, File destinationDir) {
        if (destinationDir.exists()) {
            if (!GitHelper.testRepo(log, destinationDir)) {
                log.debug("not a valid repo. checking if gitDroid.deleteSourceOnGitFailure is true")
                if (gitDroid.deleteSourceOnGitFailure) {
                    log.lifecycle("deleting destination dir")
                    if (destinationDir.deleteDir()) {
                        log.lifecycle("deleted")
                    } else {
                        log.lifecycle("failed to delete")
                    }
                }
            }
        }
    }

    /**
     * Creates gitDroid.gradle file and sets module build file to apply it. Creates local.properties in git
     * project if it does not exist. Replaces build file versions of libraries based on replaceValues
     * and replaceValuesRegex. Creates, runs, then deletes compile tasks for git project. Deletes git
     * sources keepSource is false.
     *
     * @param project the project containing git dependencies
     * @param dependency the git dependency
     * @param gitExt the GitDependencyExt for provided dependency
     * @return
     */
    def static handleCompile(Project project, ExternalModuleDependency dependency, GitDependencyExt gitExt) {
        def log = project.logger
        def gitProjectExt = ((GitProjectExt) project.extensions.findByName(GitProjectExt.NAME))
        def archivePom = gitProjectExt.shouldBuildArchivePom
        def workingDir = gitProjectExt.workingDir
        if (workingDir.exists()) {
            log.debug("workingDir found at:$workingDir.absolutePath")
        } else {
            throw new StopActionException("Git working directory missing")
        }
        def dependencyDir = new File(workingDir, dependency.name)
        def buildFileBase = new File(dependencyDir, "build.gradle")
        log.debug("remoteModule:" + gitExt.remoteModule)
        log.debug("remoteModules:" + gitExt.remoteModules)
        def moduleDir = new File(dependencyDir, (gitExt.remoteModule) ? gitExt.remoteModule : dependency.name)
        def buildFileModule = new File(moduleDir, "build.gradle")
        def gitDroid = new File(dependencyDir, "gitDroid.gradle")
        def localProperties = new File(project.rootDir, "local.properties")
        log.debug("checking if gitDroid:" + gitDroid.absolutePath + " exists:" + gitDroid.exists())
        if (gitDroid.exists()) {
            if (gitDroid.delete()) {
                log.debug("previous gitDroid deleted")
            } else {
                log.debug("previous gitDroid failed to be deleted")
            }
        }
        if (buildFileBase.exists()) {
            log.debug("buildFileBase found at $buildFileBase.absolutePath")
        } else {
            log.debug("not a gradle project")
            throw new StopActionException("not a gradle project")
        }

        if (!gitDroid.exists()) {
            log.debug("gitDroid.gradle does not exist archivePom:" + archivePom)
            def isAndroidLibrary = Utils.isAndroidLibrary(project, buildFileModule)
            log.debug("isAndroidLibrary:" + isAndroidLibrary)
            if (archivePom) {
                log.debug("creating archive file")
                addArchiveFile(log, gitProjectExt, dependency, gitExt, dependencyDir)
                def depLocalProperties = new File(dependencyDir, "local.properties")
                log.debug("dependencyLocalProperties exists:" + depLocalProperties.exists())
                if (localProperties.exists() && !depLocalProperties.exists()) {
                    addLocalFile(log, dependencyDir, localProperties)
                }
                appendArchiveFile(project, buildFileModule.exists() ? buildFileModule : buildFileBase, buildFileModule.exists())
            }

            if (buildFileBase.exists()) {
                log.debug("buildFile found at:" + buildFileBase.absolutePath)

                if (gitProjectExt.replaceValues != null) {
                    log.debug("replacing files using $gitProjectExt.replaceValues")
                    replaceValues(project, buildFileBase, gitProjectExt.replaceValues)
                    replaceValues(project, buildFileModule, gitProjectExt.replaceValues)
                }

                if (gitProjectExt.replaceValuesRegex != null) {
                    log.debug("replacing files using regex $gitProjectExt.replaceValuesRegex")
                    replaceValuesRegex(project, buildFileBase, gitProjectExt.replaceValuesRegex)
                    replaceValuesRegex(project, buildFileModule, gitProjectExt.replaceValuesRegex)
                }

                try {
                    def compileEachTask = (CompileEachTask) project.tasks.maybeCreate("compileEach-$dependency.name", CompileEachTask)
                    compileEachTask.buildFile = gitProjectExt.buildModule ? buildFileModule : buildFileBase
                    compileEachTask.dir = dependencyDir

                    HashMap<String, String> projectProperties = new HashMap<String, String>();
                    projectProperties["RELEASE_REPOSITORY_URL"] = gitProjectExt.localRepo.toString();
                    projectProperties["SNAPSHOT_REPOSITORY_URL"] = gitProjectExt.localRepo.toString();

                    StartParameter startParameter = compileEachTask.startParameter
                    startParameter.projectProperties = projectProperties

                    compileEachTask.startParameter = startParameter
                    if (gitExt.uploadDependencies) {
                        compileEachTask.tasks = Arrays.asList("clean", "assembleRelease", "uploadArchives", "uploadArchivesDeps")
                    } else {
                        compileEachTask.tasks = Arrays.asList("clean", "assembleRelease", "uploadArchives")
                    }

                    compileEachTask.run = true

                    log.debug("compileEachTask starting")
                    compileEachTask.execute()

                    log.debug("execute completed. deleting task")
                    project.tasks.remove(compileEachTask)
                } catch (e) {
                    log.warn("failed to compile:$e.message")
                    e.printStackTrace()
                }

            } else {
                log.debug("no buildFile found at:" + buildFileBase.absolutePath)
            }
            log.debug("checking if should keep source:$gitExt.keepSource")
            if (gitExt.keepSource) {
                log.debug("keepSource is true")
            } else {
                log.debug("deleting source:" + dependencyDir.absolutePath)
                if (dependencyDir.deleteDir()) {
                    log.debug("deleted")
                } else {
                    log.warn("could not delete dependency directory:$dependencyDir.absolutePath")
                }
            }
        }
    }

    /**
     * Used to replace specific included libraries' included library versions with the version defined
     * Example:
     * <pre>
     * @code
     * gitDroid{
     *  replaceValues = [
     *      "com.android.tools.build:gradle:2.0.0-alpha2" : [
     *          "com.android.tools.build:gradle:2.0.0-alpha1"
     *      ]
     *  ]
     * }
     * </pre>
     *
     * Will replace com.android.tools.build:gradle:2.0.0-alpha1 with com.android.tools.build:gradle:2.0.0-alpha2
     *
     * @param project project with git dependencies
     * @param buildFile the build file of the git dependency module or base project
     * @param replaceMap a mapping of what to replace
     * @return
     */
    static def replaceValues(Project project, File buildFile, Map<String, ArrayList<String>> replaceMap) {
        if (replaceMap == null) {
            return
        }
        if (buildFile.exists()) {
            replaceMap.each {k, v ->
                if (v == null || v.empty) {
                    project.logger.debug("buildFile:$buildFile.name list for $k is null or empty:${v?.size()}")
                    return
                }
                v?.each {r ->
                    if (r == null || r.empty) {
                        project.logger.debug("buildFile:$buildFile.name item for $k is null or empty:$r")
                        return
                    }

                    project.logger.debug("buildFile:$buildFile.name replacing $r with $k")
                    project.ant.replace(file:buildFile, token:r, value:k)
                }
            }
        }
    }

    /**
     * Used to replace a regex range of included libraries' included library versions with the version defined
     * Example:
     * <pre>
     * @code
     * gitDroid{
     *  replaceValuesRegex = [
     *      "com.android.tools.build:gradle:2.0.0-alpha3" :
     *          "(?<=['|\"])com\\.android\\.tools\\.build:gradle:.+?(?=['|\"])"
     *      ]
     *  ]
     * }
     * </pre>
     *
     * Will replace com.android.tools.build:gradle:ANYTHING with com.android.tools.build:gradle:2.0.0-alpha3
     *
     * @param project project with git dependencies
     * @param buildFile the build file of the git dependency module or base project
     * @param replaceMapRegex a mapping of what to replace
     * @return
     */
    static def replaceValuesRegex(Project project, File buildFile, Map<String, String> replaceMapRegex) {
        if (replaceMapRegex == null) {
            project.logger.debug("replaceMapRegex is null")
            return
        }
        def gitProjectExt = (GitProjectExt) project.extensions.getByName("gitDroid")

        if (buildFile.exists()) {
            replaceMapRegex.each {k, v ->
                if (!v) {
                    project.logger.debug("buildFile:$buildFile.name list for $k is null or empty:${v}")
                    return
                }
                project.logger.debug("buildFile:$buildFile.name regex replacing "+v+" with $k")
                project.ant.replaceregexp(file:buildFile,
                        match:v,
                        replace:k,
                        flags:gitProjectExt.test.regexp.flags,
                        byline:gitProjectExt.test.regexp.byLine,
                        encoding:gitProjectExt.test.regexp.encoding
                )
            }
        } else {
            project.logger.debug("buildFile doesn't exist")
        }
    }

    /**
     * Create the local.properties file for the newly created git project
     * @param log logger to use
     * @param dependencyDir git project directory
     * @param localProperties local.properties file to copy into dependencyDir
     * @return
     */
    def static addLocalFile(Logger log, File dependencyDir, File localProperties) {
        log.debug("addLocalFile init depdendencyDir:$dependencyDir.absolutePath localProperties:$localProperties.absolutePath")
        Path source = localProperties.toPath()
        Path target = new File(dependencyDir, "local.properties").toPath()
        Files.copy(source, target)
    }

    /**
     * Creates gitDroid.gradle file which adds tasks to git dependency gradle to add to local maven repo and
     * includes creating necessary dependencies into same local maven repo.
     *
     * @param log logger to use
     * @param gitProjectExt the main project GitProjectExt (gitDroid)
     * @param dependency the external dependency that is a git dependency
     * @param gitExt the git extension of the external dependency
     * @param dependencyDir the temp directory created for the git dependency project
     * @return
     */
    def static addArchiveFile(
            Logger log,
            GitProjectExt gitProjectExt,
            ExternalModuleDependency dependency,
            GitDependencyExt gitExt,
            File dependencyDir
    ) {
        log.debug("addArchiveFile init with dependency:$dependency.name and dir:$dependencyDir.absolutePath")
        log.debug("setting archive repo to:" + gitProjectExt.localRepo)

        def clearArtifact = ""
        if (!gitExt.buildJavaDocs && !gitExt.buildSources) {
            clearArtifact = """
            configurations.archives.artifacts.with { archives ->
                removeFromArchives(archives, 'javadoc.jar')
                removeFromArchives(archives, 'sources.jar')
            }
            """
        } else if (!gitExt.buildJavaDocs) {
            clearArtifact = """
            configurations.archives.artifacts.with { archives ->
                removeFromArchives(archives, 'javadoc.jar')
            }
            """
        } else if (!gitExt.buildSources) {
            clearArtifact = """
            configurations.archives.artifacts.with { archives ->
                removeFromArchives(archives, 'sources.jar')
            }
            """
        }

        def clearJavadocsSource = """
            def removeFromArchives(def archives, def search) {
                def jarArtifact
                archives.each {
                    if (it.file =~ search) {
                        jarArtifact = it
                    }
                }
                println "JAR to delete: \${jarArtifact}"
                if (jarArtifact) {
                    archives.remove(jarArtifact)
                }
            }
            $clearArtifact
"""

        new File(dependencyDir, "gitDroid.gradle") << """\
apply plugin: 'maven'

    if (project.hasProperty('android')) {
        afterEvaluate {
            android.libraryVariants.all { variant ->
                def name = variant.buildType.name
                if (name.equals("debug")) {
                    return; // Skip debug builds.
                }
                def task = project.tasks.create "gitDroidJar\${variant.name.capitalize()}", Jar
                task.dependsOn variant.javaCompile
                //Include Java classes
                task.from variant.javaCompile.destinationDir
                //Include dependent jars with some exceptions
                task.from configurations.compile.findAll {
                    it.getName() != 'android.jar' && !it.getName().startsWith('junit') && !it.getName().startsWith('hamcrest')
                }.collect {
                    it.isDirectory() ? it : zipTree(it)
                }
                artifacts.add('archives', task);
            }
        }
    }

    afterEvaluate {
        uploadArchives {
            repositories {
                mavenDeployer {
                    repository(url: "$gitProjectExt.localRepo")
                    pom.groupId = '$dependency.group'
                    pom.artifactId = '$dependency.name'
                    pom.version = '$dependency.version'
                    pom.project {
                        packaging = 'aar'
                        properties {
                            gitDroid {
                                ref = "$gitExt.ref"
                            }
                        }
                    }
                }
            }
        }
    }


$clearJavadocsSource

task uploadArchivesDeps (dependsOn:uploadArchives) {
    doLast {
        def configurationName = 'compile'
        def repoDir = "$gitProjectExt.localRepo"
        def useLifecycle = $gitProjectExt.test.useLifecycle
        def log = (useLifecycle) ? project.logger.&lifecycle : project.logger.&debug
        def Configuration configuration = project.configurations.getByName(configurationName)
        configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            def moduleVersionId = artifact.moduleVersion.id

            def moduleDir = file(repoDir + File.separator + "\${(moduleVersionId.group.replaceAll('\\\\.', '/'))}/\${moduleVersionId.name}/\${moduleVersionId.version}")
            log("artifact moduleDir:\$moduleDir.absolutePath")
            moduleDir.mkdirs()
            log("artifact moduleDir exists:\${moduleDir.exists()}")
            log("artifact.file:\$artifact.file exists:\${artifact.file.exists()} artifact.file.name:\$artifact.file.name artifact.file.isDirectory:\${artifact.file.isDirectory()}")
            copy {
                from artifact.file
                into moduleDir.absolutePath
            }
        }
        def componentIds = configuration.incoming.resolutionResult.allDependencies.collect { it.selected.id }

        def result = project.dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule, MavenPomArtifact)
                .execute()

        result.resolvedComponents.each { component ->
            def componentId = component.id
            if (componentId instanceof ModuleComponentIdentifier) {
                def moduleDir = file(repoDir + File.separator + "\${(componentId.group.replaceAll('\\\\.', '/'))}/\${componentId.module}/\${componentId.version}")
                log("pom moduleDir:\$moduleDir.absolutePath")
                moduleDir.mkdirs()
                log("pom moduleDir exists:\${moduleDir.exists()}")
                def pomFile = component.getArtifacts(MavenPomArtifact)[0].file
                log("pomFile:\$pomFile.absolutePath exists:\${pomFile.exists()} name:\$pomFile.name isDirectory:\${pomFile.isDirectory()}")
                copy {
                    from pomFile
                    into moduleDir.absolutePath
                }
            }
        }

        def resultsource = project.dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(JvmLibrary, SourcesArtifact, JavadocArtifact)
                .execute()

        resultsource.resolvedComponents.each { component ->
            if (!component) {
                return
            }
            def componentId = component.id
            if (componentId instanceof ModuleComponentIdentifier) {
                def moduleDir = file(repoDir + File.separator + "\${(componentId.group.replaceAll('\\\\.', '/'))}/\${componentId.module}/\${componentId.version}")
                log("sources javadoc moduleDir:\$moduleDir.absolutePath")
                moduleDir.mkdirs()
                log("sources javadoc moduleDir exists:\${moduleDir.exists()}")
                if (component.getArtifacts(SourcesArtifact)[0] != null) {
                    log "sources not null"

                    def sourcesFile = component.getArtifacts(SourcesArtifact)[0].file
                    log("sourcesFile:\$sourcesFile.absolutePath exists:\${sourcesFile.exists()} name:\$sourcesFile.name isDirectory:\${sourcesFile.isDirectory()}")
                    if (sourcesFile.exists()) {
                        copy {
                            from sourcesFile
                            into moduleDir.absolutePath
                        }
                    } else {
                        log("sourcesFile does not exist")
                    }
                } else {
                    log "sources are null"
                }
                if (component.getArtifacts(JavadocArtifact)[0] != null) {
                    def javadocFile = component.getArtifacts(JavadocArtifact)[0].file
                    log("javadocFile:\$javadocFile.absolutePath exists:\${javadocFile.exists()} name:\$javadocFile.name isDirectory:\${javadocFile.isDirectory()}")
                    if (javadocFile.exists()) {
                        copy {
                            from javadocFile
                            into moduleDir.absolutePath
                        }
                    } else {
                        log("javadocFile does not exist")
                    }
                } else {
                    log("javadoc is null")
                }
            }
        }
    }
}

"""

    }

    /**
     * Adds "apply from: (../)gitDroid.gradle" to the git dependency module
     *
     * @param project the main project
     * @param buildFile the buildFile to add the apply call to
     * @param parentPath if the buildFile exists in parent or a sub module
     * @return
     */
    def static appendArchiveFile(Project project, File buildFile, boolean parentPath) {
        def log = project.logger
        log.debug("init with buildFile:" + buildFile.absolutePath + " parentPath:$parentPath")
        if (!Utils.containsGitDroid(project, buildFile)) {
            buildFile.append("\napply from: '" + ((parentPath) ? "../" : "") + "gitDroid.gradle'")
        }
    }

    def static appendArchiveFileSubProject(Project project, File buildFileModule, File dependencyDir) {
        def log = project.logger
        log.debug("checking for project dependencies in:" + buildFileModule.absolutePath)
    }

    /**
     * List all git dependencies in the project to log.lifecycle
     *
     * @param project the main project
     * @return
     */
    static def listGitDependencies(Project project) {
        def log = project.logger

        def dependenciesResolved = DepResolved.newInstance()
        dependenciesResolved.list = getGitDependencies(project, dependenciesResolved.type)

        def dependenciesUnresolved = DepUnresolved.newInstance()
        dependenciesUnresolved.list = getGitDependencies(project, dependenciesUnresolved.type)

        def dependenciesNeedsBuild = DepNeedsBuild.newInstance()
        dependenciesNeedsBuild.list = getGitDependencies(project, dependenciesNeedsBuild.type)


        log.lifecycle("Dependencies Unresolved:")
        dependenciesUnresolved.list.each { ExternalModuleDependency dep, GitDependencyExt gitExt ->
            log.lifecycle("$dep.name${gitExt.toListString()}")
        }
        log.lifecycle("Dependencies Resolved:")
        dependenciesResolved.list.each { ExternalModuleDependency dep, GitDependencyExt gitExt ->
            log.lifecycle("$dep.name${gitExt.toListString()}")
        }
        log.lifecycle("Dependencies Which Need Build:")
        dependenciesNeedsBuild.list.each { ExternalModuleDependency dep, GitDependencyExt gitExt ->
            log.lifecycle("$dep.name${gitExt.toListString()}")
        }
    }
}
