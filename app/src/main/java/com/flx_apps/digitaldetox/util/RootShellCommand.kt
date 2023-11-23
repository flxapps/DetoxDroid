package com.flx_apps.digitaldetox.util

import com.stericson.RootShell.execution.Command

class RootShellCommand(
    rootCommand: String, private val onCompleted: ((Int, Int) -> Unit)?
) : Command(0, rootCommand) {
    override fun commandCompleted(id: Int, exitcode: Int) {
        super.commandCompleted(id, exitcode)
        onCompleted?.invoke(id, exitcode)
    }
}