package com.flx_apps.digitaldetox.prefs

import android.content.Context
import android.util.AttributeSet
import androidx.preference.R
import nl.invissvenska.numberpickerpreference.NumberDialogPreference

/**
 * Creation Date: 1/30/21
 * @author felix
 */
class NumberPickerPreference : NumberDialogPreference {
    var summaryText: CharSequence?

    constructor(context: Context): this(context, null)
    constructor(context: Context, attributeSet: AttributeSet?): this(context, attributeSet, R.attr.dialogPreferenceStyle)
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int): this(context, attributeSet, defStyleAttr, 0)
    constructor(context: Context, attributeSet: AttributeSet?, defStyleAttr: Int, defStyleRes: Int): super(context, attributeSet, defStyleAttr, defStyleRes) {
        val ta = context.obtainStyledAttributes(attributeSet, R.styleable.Preference, 0, 0)
        summaryText = ta.getText(R.styleable.Preference_summary)
        ta.recycle()
    }

    override fun getSummary(): CharSequence {
        return "$value $unitText" + (if (!summaryText.isNullOrEmpty()) " – $summaryText" else "")
    }
}