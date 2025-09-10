package com.flx_apps.digitaldetox.util

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import com.flx_apps.digitaldetox.DetoxDroidApplication
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Utility class for working with Shizuku service to execute privileged commands without root.
 *
 * This implementation provides a simpler approach that focuses on permission checking
 * rather than complex command execution, as command execution through Shizuku requires
 * more advanced integration that may vary across different Shizuku versions.
 */
object ShizukuUtils {
    private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
    private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

    /**
     * Checks if Shizuku is available and permission is granted.
     * @return true if Shizuku can be used to execute commands
     */
    fun isShizukuAvailable(): Boolean {
        val isInstalled = try {
            val packageManager = DetoxDroidApplication.appContext.packageManager
            packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            // Also try the manager package
            try {
                val packageManager = DetoxDroidApplication.appContext.packageManager
                packageManager.getPackageInfo("moe.shizuku.manager", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        } catch (_: Exception) {
            false
        }
        Timber.d("Shizuku installed: $isInstalled")
        if (isInstalled) {
            val shizukuRunning = try {
                Shizuku.pingBinder()
            } catch (e: Exception) {
                Timber.w("Shizuku binder not available: ${e.message}")
            }
            return shizukuRunning == true
        }
        return false
    }

    /**
     * Checks if Shizuku service is running but permission is not granted.
     * @return true if Shizuku is running but we don't have permission
     */
    fun isShizukuRunningButNotGranted(): Boolean {
        return isShizukuAvailable() && !checkSelfPermission()
    }

    /**
     * Checks if we have permission to use Shizuku.
     * @return true if permission is granted
     */
    private fun checkSelfPermission(): Boolean {
        return try {
            val permissionResult = Shizuku.checkSelfPermission()
            val hasPermission = permissionResult == PackageManager.PERMISSION_GRANTED
            Timber.d("Shizuku permission check result: $permissionResult (granted: $hasPermission)")
            hasPermission
        } catch (e: Exception) {
            Timber.w("Exception checking Shizuku permission: ${e.message}")
            false
        }
    }

    /**
     * Requests Shizuku permission from the user.
     * This will show a system dialog asking for permission.
     */
    fun requestShizukuPermission() {
        try {
            if (Shizuku.isPreV11()) {
                // Shizuku pre-v11 doesn't support runtime permission
                Timber.d("Shizuku is pre-v11, cannot request permission")
                return
            }
            if (checkSelfPermission()) {
                Timber.d("Shizuku permission already granted")
                return
            }
            Timber.d("Requesting Shizuku permission")
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (e: Exception) {
            Timber.w("Exception requesting Shizuku permission: ${e.message}")
        }
    }


    /**
     * Executes a shell command using Shizuku with elevated privileges.
     * Based on the implementation pattern from successful Shizuku apps.
     *
     * @param command The shell command to execute
     * @param onCompleted Callback with success status and output
     */
    fun executeCommand(command: String, onCompleted: ((Boolean, String) -> Unit)?) {
        Timber.d("Executing command via Shizuku: $command")
        try {
            // Try to request permission if Shizuku is running but permission not granted
            if (isShizukuRunningButNotGranted()) {
                requestShizukuPermission()

                // Wait for permission to be granted and re-call the command
                Shizuku.addRequestPermissionResultListener { requestCode, result ->
                    if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE && result == PackageManager.PERMISSION_GRANTED) {
                        Timber.d("Shizuku permission granted, re-executing command")
                        executeCommand(command, onCompleted)
                    }
                }

                onCompleted?.invoke(
                    false,
                    "Shizuku permission not granted yet. Please grant permission and try again."
                )
                return
            }

            Thread {
                try {
                    // Use the proper IShizukuService interface like in the example
                    val result = executeShizukuCommand(command)
                    val success = result.first == 0
                    val output = result.second ?: "Command completed"

                    Timber.d("Command execution completed with exit code: ${result.first}")
                    onCompleted?.invoke(success, output)

                } catch (e: Exception) {
                    Timber.e(e, "Failed to execute command via Shizuku")
                    onCompleted?.invoke(false, "Failed to execute command: ${e.message}")
                }
            }.start()

        } catch (e: Exception) {
            Timber.e(e, "Shizuku execution setup failed")
            onCompleted?.invoke(false, "Shizuku execution failed: ${e.message}")
        }
    }

    /**
     * Execute a command using the Shizuku service interface.
     * Based on the pattern from the provided example code.
     *
     * @param command The shell command to execute
     * @return Pair of exit code and output
     */
    private fun executeShizukuCommand(command: String): Pair<Int, String?> = runCatching {
        // Get the Shizuku service interface
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())

        // Create a new process (using sh instead of su since we don't need root)
        val process = service.newProcess(arrayOf("sh"), null, null)

        process.run {
            // Write the command to the process input stream
            ParcelFileDescriptor.AutoCloseOutputStream(outputStream).use { outputStream ->
                outputStream.write(command.toByteArray())
            }

            // Wait for completion and read results
            val exitCode = waitFor()
            val output = inputStream.readText.ifBlank { errorStream.readText }

            // Clean up
            destroy()

            exitCode to output
        }
    }.getOrElse {
        Timber.e(it, "Error executing Shizuku command")
        1 to it.stackTraceToString()
    }

    /**
     * Extension function to read text from a ParcelFileDescriptor
     */
    private val ParcelFileDescriptor.readText: String
        get() = ParcelFileDescriptor.AutoCloseInputStream(this).use { inputStream ->
            inputStream.bufferedReader().readText()
        }


}
