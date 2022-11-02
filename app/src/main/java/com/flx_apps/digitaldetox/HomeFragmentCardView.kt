package com.flx_apps.digitaldetox

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EViewGroup
import org.androidannotations.annotations.ViewById


/**
 * Creation Date: 11/15/20
 * @author felix
 */
@EViewGroup
open class HomeFragmentCardView(context: Context, attrs: AttributeSet) :
    MaterialCardView(context, attrs) {
    @ViewById
    lateinit var title: TextView

    @ViewById
    lateinit var overline: TextView

    @ViewById
    lateinit var subtitle: TextView

    @ViewById
    lateinit var description: TextView

    var content: LinearLayout?

    private val titleText: String
    private val overlineText: String
    private val subtitleText: String
    private val descriptionText: String

    init {
        context.theme.obtainStyledAttributes(
            attrs,
            R.styleable.HomeFragmentCardView,
            0, 0
        ).apply {
            try {
                titleText = getString(R.styleable.HomeFragmentCardView_title).orEmpty()
                subtitleText = getString(R.styleable.HomeFragmentCardView_subtitle).orEmpty()
                descriptionText = getString(R.styleable.HomeFragmentCardView_description).orEmpty()
                overlineText = getString(R.styleable.HomeFragmentCardView_overline).orEmpty()
            } finally {
                recycle()
            }
        }

        LayoutInflater.from(context).inflate(R.layout.fragment_home_card, this);
        content = findViewById(R.id.content)
    }

    @AfterViews
    fun init() {
        foregroundTintList = ColorStateList.valueOf(Color.WHITE)
        isCheckable = true
        isClickable = true
        isFocusable = true
        cardElevation = 4f
        radius = 0f
        title.text = titleText
        subtitle.text = subtitleText
        description.text = descriptionText
        overline.text = overlineText
        if (overline.text.isEmpty()) overline.visibility = GONE
    }

    override fun setChecked(checked: Boolean) {
        super.setChecked(checked)
        content?.visibility = if (checked) VISIBLE else GONE
    }

    override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
        if (content != null) {
            content?.addView(child, index, params)
            return
        }
        super.addView(child, index, params)
    }
}