package com.testfunction.internal.dependencies

import com.testfunction.extensions.dependency.GitDependencyExt
import com.testfunction.internal.enums.GitDependencyTypes
import org.gradle.api.artifacts.ExternalModuleDependency

/**
 * List of git dependencies that has not been resolved
 *
 * Created by Brandon on 1/3/2016.
 */
class DepUnresolved {
    def LinkedHashMap<ExternalModuleDependency, GitDependencyExt> list = [:]
    def type = GitDependencyTypes.UNRESOLVED
}
