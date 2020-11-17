package com.flx_apps.digitaldetox

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.androidannotations.annotations.EBean

@EBean
open class AppExceptionsListAdapter(context: Context) : RecyclerView.Adapter<AppExceptionsListAdapter.ViewHolder>() {
    lateinit var values: List<AppWhitelistItem>

    var prefs: Prefs_ = Prefs_(context)

    data class AppWhitelistItem(val title: String, val pckg: String) {
        var isException = false
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_app_whitelist_list_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        val context = holder.appIcon.context
        holder.appTitle.text = item.title
        holder.appIcon.setImageDrawable(holder.appIcon.context.packageManager.getApplicationIcon(item.pckg))
        holder.btnSetWhitelisted.setOnCheckedChangeListener { button, b ->
            item.isException = b
            prefs.edit().grayscaleExceptions().put(
                prefs.grayscaleExceptions().get().let {
                    if (b) it?.plus(item.pckg) else it?.minus(item.pckg)
                }
            ).apply()
        }
        holder.btnSetWhitelisted.isChecked = item.isException || prefs.grayscaleExceptions().get().contains(item.pckg)
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appTitle: TextView = view.findViewById(R.id.appTitle)
        val btnSetWhitelisted: CheckBox = view.findViewById(R.id.btnSetWhitelisted)
        val appIcon: ImageView = view.findViewById(R.id.appIcon)

        override fun toString(): String {
            return super.toString() + " '" + appTitle.text + "'"
        }
    }
}