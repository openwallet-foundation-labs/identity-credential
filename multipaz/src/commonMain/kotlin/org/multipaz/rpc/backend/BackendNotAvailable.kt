package org.multipaz.rpc.backend

/**
 * Thrown when [BackendEnvironment] does not exist in the current coroutine context.
 */
class BackendNotAvailable: Exception("BackendEnvironment is missing in coroutine context")