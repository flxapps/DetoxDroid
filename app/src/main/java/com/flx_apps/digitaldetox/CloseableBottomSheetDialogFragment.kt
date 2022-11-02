package com.flx_apps.digitaldetox

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

/**
 * Creation Date: 1/19/22
 * @author felix
 */
open class CloseableBottomSheetDialogFragment(var contentFragment: Fragment) : BottomSheetDialogFragment() {
    val viewId = View.generateViewId()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            id = viewId
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // init close button for the dialog
        val closeButton = MaterialButton(view.context, null, R.attr.borderlessButtonStyle).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = getText(R.string.action_close)
            setOnClickListener { dialog!!.dismiss() }
        }

        (dialog as BottomSheetDialog).behavior.state = BottomSheetBehavior.STATE_EXPANDED
        childFragmentManager
            .beginTransaction()
            .add(viewId, contentFragment)
            .runOnCommit { (view as LinearLayout).addView(closeButton) }
            .commit()
    }
}