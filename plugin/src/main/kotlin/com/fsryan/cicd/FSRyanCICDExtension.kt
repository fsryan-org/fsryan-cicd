package com.fsryan.cicd

open class FSRyanCICDExtension {
    lateinit var cicdReleaseDependentTasks: Set<String>
    lateinit var cicdDevelopDependentTasks: Set<String>
    var versionSpecifiers: Set<String> = emptySet()

    /**
     * defaults to `7`. This is the number of decimal places that the major
     * portion of the build number will start. For example,
     * `majorDecimalPlace = 7` would yield this for version 1.0.0:
     * ```
     * 1000000
     * ```
     */
    var majorDecimalPlace: Int = SemanticVersion.DEFAULT_MAJOR_DECIMAL_PLACE

    /**
     * defaults to `5`. This is the number of decimal places that the minor
     * portion of the build number will start. For example,
     * `minorDecimalPlace = 5` would yield this for version 0.1.0:
     * ```
     * 10000
     * ```
     */
    var minorDecimalPlace: Int = SemanticVersion.DEFAULT_MINOR_DECIMAL_PLACE

    /**
     * defaults to `3`. This is the number of decimal places that the patch
     * portion of the build number will start. For example,
     * `patchDecimalPlace = 3` would yield this for version 0.0.1:
     * ```
     * 100
     * ```
     *
     * Why would you want this to be 3? Well . . . there is also a build number
     * that will need some space.
     */
    var patchDecimalPlace: Int = SemanticVersion.DEFAULT_PATCH_DECIMAL_PLACE
}