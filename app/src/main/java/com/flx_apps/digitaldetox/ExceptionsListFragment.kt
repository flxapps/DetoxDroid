package com.flx_apps.digitaldetox

import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.androidannotations.annotations.AfterViews
import org.androidannotations.annotations.EFragment
import org.androidannotations.annotations.ViewById
import java.util.*

/**
 * A fragment representing a list of Items.
 */
@EFragment(R.layout.fragment_app_whitelist)
open class ExceptionsListFragment : Fragment() {
    @ViewById
    lateinit var list: RecyclerView

    @AfterViews
    fun init() {
        val pm: PackageManager? = activity?.packageManager
        val apps = pm?.getInstalledApplications(0)
        val exceptionsListItems: MutableList<ExceptionsListItemRecyclerViewAdapter.AppWhitelistItem> = ArrayList()
        apps?.iterator()?.forEach {
            exceptionsListItems.add(
                ExceptionsListItemRecyclerViewAdapter.AppWhitelistItem(
                    it.loadLabel(pm) as String,
                    it.packageName
                )
            )
        }
        exceptionsListItems.sortBy { item -> item.title }

        with(list) {
            layoutManager = LinearLayoutManager(context)
            adapter = ExceptionsListItemRecyclerViewAdapter(context).apply { values = exceptionsListItems }
        }
    }
}