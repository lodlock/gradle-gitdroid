package com.testfunction

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification

import static com.testfunction.TestTypes.*
import static org.gradle.testkit.runner.TaskOutcome.FAILED
import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static com.android.SdkConstants.ANDROID_HOME_ENV
import static com.android.SdkConstants.FN_LOCAL_PROPERTIES
import static com.android.SdkConstants.PLATFORM_WINDOWS
import static com.android.SdkConstants.PLATFORM_DARWIN
import static com.android.SdkConstants.PLATFORM_LINUX
import static com.android.SdkConstants.SDK_DIR_PROPERTY
import static com.android.SdkConstants.currentPlatform

/**
 * Android library tests
 *
 * Created by Brandon on 1/1/2016.
 */
class AndroidLibraryTest extends Specification {
    final private static Logger LOG = Logging.getLogger GradleGitDroidPlugin
    def File buildFile;
    def File localPropertiesFile;
    def File srcMainDir;
    def File manifest;
    def List<File> pluginClasspath
    def File classPath
    def File testProjectDir
    def classPathString
    def final extraArgumentsDebug = "--debug"
    def final extraArgumentsStack = "--stacktrace"
    def URI localRepo
    def boolean shouldClean = true

    def setup() {
        testProjectDir = File.createTempDir("gitDroid","");
        buildFile = new File(testProjectDir, "build.gradle")
        LOG.lifecycle("buildFile:" + buildFile + " exists:" + buildFile.exists());
        localPropertiesFile = new File(testProjectDir, "local.properties")
        def srcMainSubPath = File.separator + "src" + File.separator + "main"
        srcMainDir = new File(testProjectDir.absolutePath, srcMainSubPath)
        srcMainDir.mkdirs();
        manifest = new File(srcMainDir, "AndroidManifest.xml");

        LOG.lifecycle("manifest:" + manifest + " exists:" + manifest.exists() + " isdir:" + manifest.directory)

        def pluginClasspathResource = getClass().classLoader.findResource("plugin-classpath.txt")
        if (pluginClasspathResource == null) {
            throw new IllegalStateException("Did not find plugin classpath resource, run `testClasses` build task.")
        }
        classPath = new File("build/classes/main");
        LOG.lifecycle("file:" + classPath.absolutePath + " exists:" + classPath.exists());
        pluginClasspath = pluginClasspathResource.readLines().collect { new File(it) }
        populateLocalProperties()

        def manifestContent = '''<manifest package="com.testfunction.test"
    xmlns:android="http://schemas.android.com/apk/res/android">
    <application />
</manifest>'''
        manifest << manifestContent

        LOG.lifecycle("manifest:" + manifest);

        classPathString = pluginClasspath
                .collect { it.absolutePath.replace('\\', '\\\\') }
                .collect { "'$it'" }
                .join(", ")

        LOG.lifecycle("classPathString:" + classPathString);
    }


    def "passing test without missing dep"() {
        given:
        populateBuildFile(DEPENDENCY_NONE)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();

        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "tasks list without missing dep"() {
        given:
        populateBuildFile(DEPENDENCY_NONE)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "tasks"))
                .build();
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "tasks list with resolvable dep"() {
        given:
        populateBuildFile(DEPENDENCY_NONE)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "tasks"))
                .build();
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "failing build with missing dep without git extension"() {
        given:
        populateBuildFile(DEPENDENCY_WITHOUT_GIT)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "failing one build with missing dep with git extension"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "passing build twice with missing dep with git extension"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_AUTOMATIC_RESOLVE)
        LOG.lifecycle("buildFile:" + buildFile);

        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true))

        when:
        def buildResult = runner.buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult failed as expected output:" + buildResult.output)

        when:
        def buildResult2 = runner.build()
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult success:"+buildResult2.output)
    }

    def "passing build twice with missing dep with git extension initGitDependencies"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT)
        LOG.lifecycle("buildFile:" + buildFile);

        def runner = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(true, true, "initGitDependencies"))

        when:
        def buildResult = runner.buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult failed as expected output:" + buildResult.output)

        when:
        def buildResult2 = runner.build()
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult success:"+buildResult2.output)
    }

    def "fail first build missing dep using git direct call initGitDependencies"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_COMMIT)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "fail build missing dep using git direct call initGitDependencies wrong commit"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_COMMIT_WRONG)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "fail build missing dep using git direct call initGitDependencies branch"() {
        shouldClean = false
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_BRANCH)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "pass build twice missing dep using git direct call listGitDependencies branch"() {
        shouldClean = false
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_BRANCH)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)

        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "listGitDependencies"))
                .build();
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "pass build twice missing dep using git direct call listGitDependencies tag"() {
        shouldClean = false
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_TAG)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)

        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "listGitDependencies"))
                .build();
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "pass build twice missing dep using git direct call initGitDependencies branch"() {
        shouldClean = false
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_BRANCH)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)

        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "pass build twice missing dep using git direct call initGitDependencies branch keepUpdated"() {
        shouldClean = false
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_BRANCH_KEEP_UPDATED)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)

        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "initGitDependencies dep without git gitConfig"() {
        given:
        populateBuildFile(DEPENDENCY_WITHOUT_GIT_DIFF_CONFIG)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();
        buildResult.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
        buildResult.output.contains('BUILD SUCCESSFUL')
        buildResult.task(":initGitDependencies").outcome == SUCCESS
    }

    def "passing test dep with git gitConfig"() {
        given:
        populateBuildFile(DEPENDENCY_WITH_GIT_DIFF_CONFIG)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();
        buildResult.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def "passing multi dep with one git"() {
        given:
        populateBuildFile(DEPENDENCY_MULTIPLE_WITH_ONE_GIT)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build();
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "passing multi dep with both git"() {
        given:
        populateBuildFile(DEPENDENCY_MULTIPLE_WITH_ALL_GIT)
        LOG.lifecycle("buildFile:" + buildFile)

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail()
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
        when:
        def buildResult2 = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .build()
        buildResult2.tasks(SUCCESS)
        then:
        LOG.lifecycle("buildResult2 output:" + buildResult2.output)
    }

    def "failing test dep non existent"() {
        given:
        populateBuildFile(DEPENDENCY_NON_EXISTENT)
        LOG.lifecycle("buildFile:" + buildFile);

        when:
        def buildResult = GradleRunner.create()
                .withProjectDir(testProjectDir)
                .withTestKitDir(testKitDir)
                .withPluginClasspath(pluginClasspath)
                .withArguments(populateArguments(false, true, "initGitDependencies"))
                .buildAndFail();
        buildResult.tasks(FAILED)
        then:
        LOG.lifecycle("buildResult output:" + buildResult.output)
    }

    def cleanup() {
        LOG.lifecycle("cleanup init for dir:" + testProjectDir.absolutePath)
        if (shouldClean) {
            testProjectDir.deleteDir()
        }
    }

    private void populateBuildFile(def TestTypes testType) {
        LOG.lifecycle("populateBuildFile with TestTypes:${testType.name()}")
        def dependencyNone = ""

        def dependencyNoGit = """\
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'ASDF',
            ext: 'aar'
    )
"""

        def dependencyWithGit = """\
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
"""


        def dependencyWithGitCommit = """\
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                ref : '4ff48898569e96c1c3074214e3226730d99e4df2',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
"""

        def dependencyWithGitCommitWrong = """\
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                ref : '4ff48898569e96c1c3074214e3226730d99e4df2_no',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
"""

        def dependencyWithGitCommitTag = """\
    compile (group: 'com.nostra13',
            name:'universalimageloader',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/nostra13/Android-Universal-Image-Loader.git',
                ref : 'v1.9.4',
                buildJavaDocs : true,
                keepUpdated : false,
                remoteModule: 'library',
                keepSource : true
        ]
    }
"""

        def dependencyWithGitCommitBranch = """\
    compile (group: 'com.nostra13',
            name:'universalimageloader',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/nostra13/Android-Universal-Image-Loader.git',
                ref : 'new_memory_cache_api',
                buildJavaDocs : true,
                keepUpdated : false,
                remoteModule: 'library',
                keepSource : true
        ]
    }
"""

        def dependencyWithGitCommitBranchKeepUpdated = """\
    compile (group: 'com.nostra13',
            name:'universalimageloader',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/nostra13/Android-Universal-Image-Loader.git',
                ref : 'new_memory_cache_api',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'library',
                keepSource : true
        ]
    }
"""

        def dependencyMultipleWithGit = """\
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
    compile (group: 'com.kaopiz',
            name:'kprogresshud',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/Kaopiz/KProgressHUD.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'kprogresshud',
                keepSource : true
        ]
    }
"""

        def dependencyMultipleOneWithGit = """\
    compile 'com.kaopiz:kprogresshud:1.0.1'
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
"""

        def dependencyNonExistent = """\
    compile (group: 'com.testfunction-non-existent_fdsa8023fja',
            name:'non-existent_ifa8vb0gjnfj',
            version:'BLAH',
            ext: 'aar')
"""

        def dependencyNoGitDiffConfig = """\
    gitConfig (group: 'com.testfunction',
            name:'lumberjill',
            version:'ADF',
            ext: 'aar')
"""

        def dependencyWithGitDiffConfig = """\
    gitConfig (group: 'com.testfunction',
            name:'lumberjill',
            version:'FAKE',
            ext: 'aar'
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }
"""

        def gitConfig = """\
    configurations {
        gitConfig
    }
"""

        def config = ""
        def depStr = ""

        switch (testType) {
            case DEPENDENCY_NONE:
                depStr += dependencyNone
                break
            case DEPENDENCY_WITHOUT_GIT:
                depStr += dependencyNoGit
                break
            case DEPENDENCY_WITH_GIT:
                depStr += dependencyWithGit
                break
            case DEPENDENCY_WITH_GIT_COMMIT:
                depStr += dependencyWithGitCommit
                break
            case DEPENDENCY_WITH_GIT_COMMIT_WRONG:
                depStr += dependencyWithGitCommitWrong
                break
            case DEPENDENCY_WITH_GIT_TAG:
                depStr += dependencyWithGitCommitTag
                break
            case DEPENDENCY_WITH_GIT_BRANCH:
                depStr += dependencyWithGitCommitBranch
                break
            case DEPENDENCY_WITH_GIT_BRANCH_KEEP_UPDATED:
                depStr += dependencyWithGitCommitBranchKeepUpdated
                break
            case DEPENDENCY_WITH_GIT_AUTOMATIC_RESOLVE:
                depStr += dependencyWithGit
                break
            case DEPENDENCY_WITHOUT_GIT_DIFF_CONFIG:
                depStr += dependencyNoGitDiffConfig
                config = gitConfig
                break
            case DEPENDENCY_WITH_GIT_DIFF_CONFIG:
                depStr += dependencyWithGitDiffConfig
                config = gitConfig
                break
            case DEPENDENCY_MULTIPLE_WITH_ONE_GIT:
                depStr += dependencyMultipleOneWithGit
                break;
            case DEPENDENCY_MULTIPLE_WITH_ALL_GIT:
                depStr += dependencyMultipleWithGit
                break;
            case DEPENDENCY_NON_EXISTENT:
                depStr += dependencyNonExistent
                break;
            default:
                depStr += dependencyNone
        }

        def buildFileContent = """\
    buildscript {
        repositories {
            jcenter()
        }
        dependencies {
            classpath 'com.android.tools.build:gradle:1.5.0'
        }
    }
    plugins {
        id 'com.testfunction.gradle-gitdroid'
    }

    allprojects {
        repositories {
            jcenter()
            maven {
                url mavenLocal().url
            }
            maven {
                url gitDroid.localRepo
            }
        }
    }

    gitDroid {
        workingDir = getRootDir().absolutePath + File.separator + "tmp"
        localRepo = uri getRootDir().absolutePath + File.separator + "local-repo"
        automaticResolve = ${testType == DEPENDENCY_WITH_GIT_AUTOMATIC_RESOLVE}
        test {
            pathLengthDieOnFailure = false
        }
        replaceValuesRegex = [
            "com.android.tools.build:gradle:2.0.0-alpha3" :
            "(?<=['|\\"])com\\\\.android\\\\.tools\\\\.build:gradle:.+?(?=['|\\"])"
        ]
    }

    apply plugin: 'com.android.library'

    android {
        compileSdkVersion 23
        buildToolsVersion "23.0.2"

        defaultConfig {
            minSdkVersion 16
            targetSdkVersion 23
            versionCode 1
            versionName "1.0"
        }
        buildTypes {
            release {
                minifyEnabled false
                proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
            }
        }
    }

$config

    repositories {
        jcenter()
        maven {
            url mavenLocal().url
        }
        maven {
            url gitDroid.localRepo
        }
    }

    dependencies {
        $depStr
    }
""";
        LOG.lifecycle("buildFileContent:$buildFileContent")
        buildFile << buildFileContent
    }

    def populateLocalProperties() {
        def path = ''

        def isWindows = false
        if (currentPlatform() == PLATFORM_WINDOWS) {
            LOG.lifecycle("isWindows setting to true")
            isWindows = true
        }

        def androidHome = System.getenv(ANDROID_HOME_ENV)
        LOG.lifecycle("androidHome:$androidHome")
        if (androidHome) {
            def sdkDir = new File(androidHome)
            if (sdkDir.exists()) {
                LOG.lifecycle("Found $ANDROID_HOME_ENV at '$androidHome'. Writing to $FN_LOCAL_PROPERTIES.")
                path = androidHome
            } else {
                LOG.lifecycle("Found $ANDROID_HOME_ENV at '$androidHome' but directory is missing.")
            }
        }

        LOG.lifecycle("path:$path")

        if (!path) {
            LOG.lifecycle("path empty")
            def home = ''
            def androidSdk = null
            LOG.lifecycle("home:$home")
            if (currentPlatform() == PLATFORM_WINDOWS) {
                home = System.getenv("HOMEDRIVE") + System.getenv("HOMEPATH")
                androidSdk = new File(home,
                        File.separator + "AppData" +
                                File.separator + "Local" +
                                File.separator + "Android" +
                                File.separator + "sdk")

            } else if (currentPlatform() == PLATFORM_DARWIN) {
                home = System.getenv("HOME")
                androidSdk = new File(home,
                        File.separator + "Library" +
                                File.separator + "Android" +
                                File.separator + "sdk")

            } else if (currentPlatform() == PLATFORM_LINUX) {
                home = System.getenv("HOME")
                androidSdk = new File(File.separator + "usr" +
                        File.separator + "local" +
                                File.separator + "android-sdk")

            }
            LOG.lifecycle("home:$home androidSdk:"+androidSdk)
            if (androidSdk?.exists() && androidSdk?.isDirectory()) {
                path = androidSdk.absolutePath
            } else {
                if (home) {
                    androidSdk = new File(home, ".android-sdk")
                    if (androidSdk?.exists() && androidSdk?.isDirectory()) {
                        path = androidSdk.absolutePath
                    }
                } else {
                    LOG.lifecycle("could not get home environment")
                }
            }
        }

        if (!path) {
            LOG.lifecycle("failing to make local.properties")
            return
        }


        if (isWindows) {
            path = path.replace "\\", "\\\\"
            path = path.replace ":", "\\:"
        }

        localPropertiesFile << "$SDK_DIR_PROPERTY=$path\n"
        LOG.lifecycle("localPropertiesFile created :"+localPropertiesFile+" exists:"+localPropertiesFile.exists())
    }

    def populateArguments(boolean debug, boolean stacktrace, String... args) {
        LOG.lifecycle("populateArguments init with debug:$debug stacktrace:$stacktrace and args:$args")
        def out = [];
        args.each {
            LOG.lifecycle("populateArguments adding arg:$it")
            out << it
        }
        if (debug) {
            LOG.lifecycle("populateArguments debug is true")
            out << extraArgumentsDebug
        }
        if (stacktrace) {
            LOG.lifecycle("populateArguments stacktrace is true")
            out << extraArgumentsStack
        }
        LOG.lifecycle("populateArguments returning args:$out")
        return out
    }


    private static File getTestKitDir() {
        def gradleUserHome = System.getenv("GRADLE_USER_HOME")
        if (!gradleUserHome) {
            gradleUserHome = new File(System.getProperty("user.home"), ".gradle").absolutePath
        }
        return new File(gradleUserHome, "testkit")
    }

    private static void writeFile(File destination, String content) throws IOException {
        BufferedWriter output = null;
        try {
            output = new BufferedWriter(new FileWriter(destination));
            output.write(content);
        } finally {
            if (output != null) {
                output.close();
            }
        }
    }
}
