package com.flx_apps.digitaldetox.ui.widgets.apps

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionTopBar(
    title: String,
    showSearchBar: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    navigationIcon: @Composable () -> Unit,
    searchContentDescription: String,
    closeSearchContentDescription: String,
    onOpenFilters: (() -> Unit)? = null,
    filtersEnabled: Boolean = true,
    filtersContentDescription: String = "",
    trailingActions: @Composable (RowScope.() -> Unit) = {},
) {
    TopAppBar(
        navigationIcon = navigationIcon,
        title = {
            AnimatedContent(targetState = showSearchBar, label = "ToggleSelectionSearchBar") {
                AnimatedVisibility(visible = it) {
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onSearch = {},
                        active = false,
                        onActiveChange = {},
                        trailingIcon = {
                            IconButton(onClick = onToggleSearch) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = closeSearchContentDescription
                                )
                            }
                        }
                    ) {}
                }
                AnimatedVisibility(visible = !it) {
                    Text(title)
                }
            }
        },
        actions = {
            AnimatedContent(targetState = showSearchBar, label = "ToggleSelectionSearchAction") {
                AnimatedVisibility(visible = !it) {
                    IconButton(onClick = onToggleSearch) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = searchContentDescription
                        )
                    }
                }
            }
            if (onOpenFilters != null) {
                IconButton(enabled = filtersEnabled, onClick = onOpenFilters) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = filtersContentDescription
                    )
                }
            }
            trailingActions()
        }
    )
}
