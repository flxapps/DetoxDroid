package com.flx_apps.digitaldetox

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.widget.ContentLoadingProgressBar
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flx_apps.digitaldetox.prefs.Prefs_
import com.mikepenz.fastadapter.FastAdapter
import com.mikepenz.fastadapter.adapters.ItemAdapter
import com.mikepenz.fastadapter.items.AbstractItem
import kotlinx.android.synthetic.main.app_bar_main.*
import org.androidannotations.annotations.*
import org.androidannotations.annotations.sharedpreferences.Pref
import java.util.*


/**
 * A fragment representing a list of Items.
 */

@EFragment(R.layout.fragment_app_exceptions)
@OptionsMenu(R.menu.menu_exceptions_list_fragment)
open class AppExceptionsListFragment : Fragment() {
    val RANDOM_STRING = "782dVH6W9G6Kb1ogbOC1"

    @ViewById
    lateinit var list: RecyclerView

    @ViewById
    lateinit var loadingProgressBar: ContentLoadingProgressBar

    @Pref
    lateinit var prefs: Prefs_

    @FragmentArg
    lateinit var exceptionsSetKey: String

    lateinit var fastAdapter: FastAdapter<AppItem>
    var itemAdapter: ItemAdapter<AppItem>? = null

    data class ItemFilter(
        var showUserApps: Boolean = true,
        var showSystemApps: Boolean = true
    )
    val itemFilter = ItemFilter()

    private val isAppsListLoaded = MutableLiveData<Boolean>(false)

    @AfterViews
    fun init() {
        loadingProgressBar.show()
        loadApps()

        when (exceptionsSetKey) {
            prefs.grayscaleExceptions().key() -> requireActivity().toolbar.subtitle = getString(R.string.home_grayscale)
            prefs.breakDoomScrollingExceptions().key() -> requireActivity().toolbar.subtitle = getString(R.string.home_doomScrolling)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        requireActivity().toolbar.subtitle = ""
    }

    @Background
    open fun loadApps() {
        val pm: PackageManager? = activity?.packageManager
        val apps = pm?.getInstalledApplications(0)
        val appList: MutableList<AppItem> = ArrayList()
        val exceptions = prefs.sharedPreferences.getStringSet(exceptionsSetKey, emptySet())!!
        val isSystemAppMask = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
        apps?.iterator()?.forEach {
            appList.add(
                AppItem().apply {
                    name = it.loadLabel(pm) as String
                    pckg = it.packageName
                    Log.d("appItems", "appItem: $name $pckg");
                    isException = exceptions.contains(it.packageName)
                    isSystemApp = (it.flags and isSystemAppMask) !== 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isAdded) {
                        category = ApplicationInfo.getCategoryTitle(context, it.category)?.toString()
                    }
                }
            )
        }
        appList.sortBy { item -> item.name }
        initAdapter(appList)
    }

    @UiThread
    open fun initAdapter(appList: List<AppItem>) {
        if (!this::list.isInitialized) return

        itemAdapter = ItemAdapter()
        itemAdapter?.itemFilter?.filterPredicate = { item: AppItem, constraint: CharSequence? ->
            var showItem = RANDOM_STRING == constraint ||
                    item.name.orEmpty().toLowerCase(Locale.getDefault())
                        .contains(constraint.toString().toLowerCase(Locale.getDefault())) ||
                    item.category.orEmpty().toLowerCase(Locale.getDefault())
                        .contains(constraint.toString().toLowerCase(Locale.getDefault()))
            showItem = showItem && ((!item.isSystemApp && itemFilter.showUserApps) || (item.isSystemApp && itemFilter.showSystemApps))
            showItem
        }
        itemAdapter?.setNewList(appList)
        itemAdapter?.filter(RANDOM_STRING)

        fastAdapter = FastAdapter.with(itemAdapter!!)
        fastAdapter.onClickListener = { view, adapter, item, position ->
            item.isException = !item.isException
            prefs.sharedPreferences.edit().putStringSet(
                exceptionsSetKey,
                prefs.sharedPreferences.getStringSet(exceptionsSetKey, emptySet())!!.let {
                    if (item.isException) it.plus(item.pckg) else it.minus(item.pckg)
                }
            ).apply()
            fastAdapter.notifyAdapterItemChanged(position, item)
            true
        }

        with(list) {
            layoutManager = LinearLayoutManager(context)
            adapter = fastAdapter
        }
        loadingProgressBar.hide()
        isAppsListLoaded.value = true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        val searchView = (menu.findItem(R.id.menu_search).actionView as SearchView)
        searchView.setOnQueryTextListener(object :
            SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterItemAdapterByString(newText)
                return true
            }
        })
        isAppsListLoaded.observe(viewLifecycleOwner) { isLoaded ->
            if (isLoaded && searchView.query.isNotEmpty()) {
                filterItemAdapterByString(searchView.query.toString())
            }
        }
    }

    private fun filterItemAdapterByString(query: String?) {
        itemAdapter?.filter(if (query.isNullOrEmpty()) RANDOM_STRING else query)
    }

    @OptionsItem(R.id.menu_filter_systemApps, R.id.menu_filter_userApps)
    fun filterClicked(menuItem: MenuItem) {
        menuItem.isChecked = !menuItem.isChecked
        when (menuItem.itemId) {
            R.id.menu_filter_userApps -> itemFilter.showUserApps = menuItem.isChecked
            R.id.menu_filter_systemApps -> itemFilter.showSystemApps = menuItem.isChecked
        }
        itemAdapter?.itemFilter?.filter(itemAdapter?.itemFilter?.constraint)
    }

    open class AppItem : AbstractItem<AppItem.ViewHolder>() {
        var name: String? = null
        var pckg: String? = null
        var category: String? = null
        var isException = false
        var isSystemApp = false

        /** defines the type defining this item. must be unique. preferably an id */
        override val type: Int
            get() = 0

        /** defines the layout which will be used for this item in the list */
        override val layoutRes: Int
            get() = R.layout.fragment_app_exceptions_list_item

        override fun getViewHolder(v: View): ViewHolder {
            return ViewHolder(v)
        }

        class ViewHolder(view: View) : FastAdapter.ViewHolder<AppItem>(view) {
            val title: TextView = view.findViewById(R.id.appTitle)
            val subtitle: TextView = view.findViewById(R.id.appPackage)
            val btnToggleExceptionState: SwitchCompat = view.findViewById(R.id.btnToggleExceptionState)
            val icon: ImageView = view.findViewById(R.id.appIcon)

            override fun bindView(item: AppItem, payloads: List<Any>) {
                title.text = item.name
                subtitle.text = item.category
                if (subtitle.text.isNullOrEmpty()) {
                    subtitle.visibility = View.GONE
                }
                icon.setImageDrawable(icon.context.packageManager.getApplicationIcon(item.pckg!!))
                btnToggleExceptionState.isChecked = item.isException
            }

            override fun unbindView(item: AppItem) {
                title.text = null
                subtitle.text = null
                subtitle.visibility = View.VISIBLE
                btnToggleExceptionState.isChecked = false
                icon.setImageDrawable(null)
            }
        }
    }

}