package com.android.identity.flow.environment

/**
 * Simple interface to access configuration parameters.
 *
 * Parameters can be organized in tree-like structures with dots separating the names of the
 * nodes in that tree, i.e. "componentClass.componentName.valueName".
 */
interface Configuration {
    fun getProperty(name: String): String?
}