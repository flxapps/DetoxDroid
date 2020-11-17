package com.flx_apps.digitaldetox

import android.content.pm.PackageManager
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.androidannotations.annotations.*
import java.util.*

/**
 * A fragment representing a list of Items.
 */
@EFragment(R.layout.fragment_app_exceptions)
open class AppExceptionsListFragment : Fragment() {
    @ViewById
    lateinit var list: RecyclerView

    @ViewById
    lateinit var loadingProgressBar: ContentLoadingProgressBar

    @AfterViews
    fun init() {
        loadingProgressBar.show()
        loadApps()
    }

    @Background
    open fun loadApps() {
        val pm: PackageManager? = activity?.packageManager
        val apps = pm?.getInstalledApplications(0)
        val appList: MutableList<AppExceptionsListAdapter.AppWhitelistItem> = ArrayList()
        apps?.iterator()?.forEach {
            appList.add(
                AppExceptionsListAdapter.AppWhitelistItem(
                    it.loadLabel(pm) as String,
                    it.packageName
                )
            )
        }
        appList.sortBy { item -> item.title }
        initAdapter(appList)
    }

    @UiThread
    open fun initAdapter(appList: List<AppExceptionsListAdapter.AppWhitelistItem>) {
        if (!this::list.isInitialized) return

        with(list) {
            layoutManager = LinearLayoutManager(context)
            adapter = AppExceptionsListAdapter(context).apply { values = appList }
        }
        loadingProgressBar.hide()
    }
}