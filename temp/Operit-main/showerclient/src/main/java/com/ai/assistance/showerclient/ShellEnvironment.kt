package com.ai.assistance.showerclient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Identity for executing shell commands.
 */
enum class ShellIdentity {
    DEFAULT,
    SHELL,
    ROOT,
}

/**
 * Result of executing a shell command.
 */
data class ShellCommandResult(
    val success: Boolean,
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
)

/**
 * Minimal abstraction for running shell commands with an identity.
 *
 * Host apps must provide an implementation and assign it to [ShowerEnvironment.shellRunner]
 * during application startup.
 */
fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}

/**
 * Global environment configuration for the Shower client library.
 */
object ShowerEnvironment {

    @Volatile
    var shellRunner: ShellRunner? = null
}
