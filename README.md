# Gradle GitDroid (gradle-gitdroid)

This gradle plugin is designed for allowing Android dependencies using git. This allows you to use any Android library 
from a git repo based on commit or branch. 

### What this plugin does
* Looks for dependencies that have a git extension
* Clone/pull from public or private repo
* Replace repo project library versions
* Compile git project
* Upload compiled git project and all it's dependencies to local maven repo

### Usage
To use this plugin:
####1. Add to classpath
`classpath group: 'com.testfunction', name: 'gradle-gitdroid', version: '0.1'` to your root `build.gradle` file 
in the buildscript dependencies section.
 
```groovy
 buildscript {
     repositories {
         jcenter()
         maven {
             url mavenLocal().url
         }
 
     }
     dependencies {
         classpath 'com.android.tools.build:gradle:2.0.0-alpha3'
         classpath group: 'com.testfunction', name: 'gradle-gitdroid', version: '0.1'
     }
 }
```
 
In your module's `build.gradle` file add `apply plugin: 'gradle-gitdroid'`

####2. Configure Gradle GitDroid in your module's `build.gradle` file with the `gitDroid` closure:
 
```groovy
gitDroid {
    workingDir = "C:/tmp/${rootProject.name}/gitDroid"
    localRepo = getRepositories().mavenLocal().url
    automaticResolve = false
}
```

If `automaticResolve` is set to `true` then every time the project is evaluated this plugin will look for git 
dependencies and if necessary compile them.

####3. Add a git extension to a dependency
```groovy
compile (group: 'com.testfunction',
        name:'lumberjill',
        version:'HEAD',
        ext: 'aar',
        transitive: true
).ext {
    git = [
            repo : 'https://github.com/lodlock/LumberJill.git',
            remoteModule: 'lumberjill'
    ]
}
```


### Gradle GitDroid properties

#####Valid gitDroid properties:

Property | Expects | Default | Description
------------- | ------------- | ------------- | -------------
automaticResolve | boolean | false | Should Gradle GitDroid automatically handle git dependencies
buildModule | boolean | true | Should run build commands against the supplied module or against base project 
deleteSourceOnGitFailure | boolean | true | If git command fails delete the current git source
localRepo | URI | repositories.mavenLocal().url | Location to upload to. **Should be included in dependency repositories**
replaceValues | Map<String, ArrayList<String>> | null | Search and replace strings in git dependency build.gradle file
replaceValuesRegex | Map<String, String> | null | Search and replace all matching strings in git dependency build.gradle file
test | test closure | | See gitDroid.test
workingDir | Directory | rootProject + "git-tmp" | The directory to clone/pull contents into to work with

#####Valid gitDroid.test properties: (Most do not need to be messed with)

Property | Expects | Default | Description
------------- | ------------- | ------------- | -------------
filterConfig | boolean | true | Filter dependencies based on config state
filterDependenciesByExternalModule | boolean | true | Filter dependencies restrict to ExternalModuleDependency
forceRunInit | boolean | false | Force init to run every time. (Slow)
pathLengthDieOnFailure | boolean | true | On Windows machines die out if potential file path length would be too long
regexp | regexp closure | | See gitDroid.test.regexp
useLifecycle | boolean | false | Log archive task to lifecycle instead of debug

#####Valid gitDroid.test.regexp properties:

Property | Expects | Default | Description
------------- | ------------- | ------------- | -------------
flags | String | "g" | g global, i case insensitive, m multiline, s singleline
byLine | boolean | false | Search one line at a time
encoding | String | "UTF-8" | Encoding to use for search / replace

#####Full Gradle GitDroid DSL:
```groovy
gitDroid {
    automaticResolve = false
    buildModule = true
    deleteSourceOnGitFailure = true
    localRepo = repositories.mavenLocal().url
    //optional replace all android build tools alpha1 with alpha3
    replaceValues = [ "com.android.tools.build:gradle:2.0.0-alpha3" : [
        "com.android.tools.build:gradle:2.0.0-alpha1"
        ]
    ]
    //optional replace all android build tool versions with alpha3
    replaceValuesRegex = [ "com.android.tools.build:gradle:2.0.0-alpha3" : 
        "(?<=['|\"])com\\.android\\.tools\\.build:gradle:.+?(?=['|\"])"
    ]
    test {
        filterConfig = true
        filterDependenciesByExternalModule = true
        forceRunInit = false
        pathLengthDieOnFailure = true
        regexp {
            flags = "g"
            byLine = false
            encoding = "UTF-8"
        }
        useLifecycle = false
    }
    workingDir = "C:/tmp/${rootProject.name}/gitDroid"
}
```

### Declaring dependencies
Declare dependencies and attach git extension. *It is recommended to include aar extension and setting transitive to true*

```groovy
compile (group: 'com.testfunction',
        name:'lumberjill',
        version:'HEAD',
        ext: 'aar',
        transitive: true
).ext {
    git = [
            repo : 'https://github.com/lodlock/LumberJill.git',
            keepUpdated : true,
            remoteModule: 'lumberjill',
            keepSource : true
    ]
}
```

OR

```groovy
compile ('com.testfunction:lumberjill:HEAD@aar') {
        transitive = true
}.ext {
    git = [
            repo : 'https://github.com/lodlock/LumberJill.git',
            keepUpdated : true,
            remoteModule: 'lumberjill',
            keepSource : true
    ]
}
```

*Dependency version number can be anything*

### Git properties
**Bold** entries are required

Property | Expects | Default | Description
------------- | ------------- | ------------- | -------------
buildJavaDocs | boolean | false | Not yet implemented
buildSource | boolean | false | Not yet impelemented
credentials | Map | | sshKey or username and password
forceBuild | boolean | false | Used internally to force a build of the dependency
keepSource | boolean | false | Keep the cloned source code after completion
keepUpdated | boolean | false | This dependency should regularly check for newer versions
ref | String | | Commit, Tag, or Branch reference desired
refType | Enum | Automatic | Can be Automatic, Branch, Commit, or Tag 
**remoteModule** | String | | The module desired in the project
remoteModules | ArrayList | null | Multiple desired modules from the project
**repo** | String | | The repository to clone/pull from
uploadDependencies | boolean | true | Upload a copy of all of the git project dependencies



Gradle GitDroid dependency git DSL:

```groovy
git = [
    buildJavaDocs : true,
    buildSource : true,
    credentials : [ sshKey:'path/to/ssh/key', username:passedUserName, password:passedPassword ],
    forceBuild : false,
    keepSource : false,
    keepUpdated : false,
    ref : 'ab0886261d09c867c012647fbccb9342082c7880', //branch, commit, or tag
    refType : 'Automatic',
    remoteModule : 'lumberjill', //the directory name of the module desired
    remoteModules : ['module1', 'module2'],
    repo : 'https://github.com/lodlock/LumberJill.git', //the url of the repo
    uploadDependencies : true //also upload a copy of dependencies to the gitDroid.localRepo
]
```


### Basic Example
rootProject `build.gradle`
```groovy
 buildscript {
     repositories {
         jcenter()
         maven {
             url mavenLocal().url
         }
 
     }
     dependencies {
         classpath 'com.android.tools.build:gradle:2.0.0-alpha3'
         classpath group: 'com.testfunction', name: 'gradle-gitdroid', version: '0.1'
     }
 }
 
 allprojects {
     buildDir = "C:/tmp/${rootProject.name}/${project.name}" //short buildDir to avoid Windows path limit
     repositories {
         jcenter()
         maven {
             url mavenLocal().url
         }
     }
 }
 
 task clean(type: Delete) {
     delete rootProject.buildDir
 }

```

module `build.gradle`
```groovy
apply plugin: 'com.android.application'
apply plugin: 'gradle-gitdroid'

android {
    compileSdkVersion 23
    buildToolsVersion "23.0.2"

    defaultConfig {
        applicationId "com.testfunction.testgradleplugin"
        minSdkVersion 16
        targetSdkVersion 23
        versionCode 1
        versionName "1.0"
        generatedDensities = []
    }
    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android.txt'), 'proguard-rules.pro'
        }
    }
}

dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    testCompile 'junit:junit:4.12'
    
    compile 'com.android.support:appcompat-v7:23.1.1'
    
    compile (group: 'com.testfunction',
            name:'lumberjill',
            version:'HEAD',
            ext: 'aar',
            transitive: true
    ).ext {
        git = [
                repo : 'https://github.com/lodlock/LumberJill.git',
                buildJavaDocs : true,
                keepUpdated : true,
                remoteModule: 'lumberjill',
                keepSource : true
        ]
    }

    compile ('com.github.michaldrabik:tapbarmenu:gitDroid@aar') {
        transitive = true
    }.ext {
        git = [
                repo : 'https://github.com/michaldrabik/TapBarMenu.git',
                ref : 'd17b48f167f3d121c00dfb5ad7fd7f39b8fc18d2',
                remoteModule: 'library',
                keepSource: true
        ]
    }

    compile ("com.github.DeveloperPaul123:FilePickerLibrary:gitDroid@aar") {
        transitive = true
    }.ext {
        git = [
                repo: 'https://github.com/DeveloperPaul123/FilePickerLibrary.git',
                ref : '02782b398c6ab590947a3beba6a0d2545c4caf4d',
                remoteModule: 'FPlib',
                keepSource: true
        ]
    }
}

gitDroid {
    workingDir = "C:/tmp/${rootProject.name}/gitDroid"
    localRepo = getRepositories().mavenLocal().url
    automaticResolve = false
    test {
        pathLengthDieOnFailure = false
    }
    replaceValuesRegex = [
            "com.android.tools.build:gradle:2.0.0-alpha3" :
            "(?<=['|\"])com\\.android\\.tools\\.build:gradle:.+?(?=['|\"])"
    ]
}
```

### Notes
If you are working in Windows you should set a short workingDir path to help avoid reaching the Windows path limit.
