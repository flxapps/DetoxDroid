package com.flx_apps.digitaldetox

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.DialogFragment


/**
 * Creation Date: 1/22/22
 * @author felix
 */
class UninstallDetoxDroidDialogFragment : DialogFragment(), DialogInterface.OnClickListener {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(context)
            .setTitle(R.string.navigation_uninstall_dialog_title)
            .setMessage(R.string.navigation_uninstall_dialog_message)
            .setPositiveButton(R.string.navigation_uninstall, this)
            .setNegativeButton(R.string.action_cancel, null)
            .create()
    }

    override fun onClick(dialog: DialogInterface?, which: Int) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            if (DetoxDroidDeviceAdminReceiver.isGranted(requireContext())) {
                DetoxDroidDeviceAdminReceiver.revokePermission(requireContext())
            }
            startActivity(
                Intent(
                    Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
    }
}