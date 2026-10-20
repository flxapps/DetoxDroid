package com.flx_apps.digitaldetox.ui.screens.app_exceptions

import android.icu.text.ListFormatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NoAdultContent
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.IconCard
import com.flx_apps.digitaldetox.ui.widgets.apps.AppListSectionHeader
import com.flx_apps.digitaldetox.ui.widgets.apps.AppSelectionListItem
import com.flx_apps.digitaldetox.websites.InstalledBrowsers
import com.flx_apps.digitaldetox.websites.WebsiteCollection
import com.flx_apps.digitaldetox.websites.Websites
import java.text.NumberFormat

/**
 * The website half of an app list: a field to add a site, the sites added so far, the collections
 * that come with the app, and which of the phone's browsers DetoxDroid can read. Websites are
 * entries of the same list as the apps (see [Websites]), so the list type above applies to them
 * the same way.
 *
 * @param query The search from the top bar, which narrows the sites and the collections here as it
 *   narrows the apps on the other half. It finds the collections folded under "Advanced" as well.
 * @param collectionTitle The name of a collection, for the search.
 * @param advancedExpanded Whether the [WebsiteCollection.advanced] collections are unfolded.
 * @param copyButton The "Copy from…" button, which copies apps and websites alike.
 */
fun LazyListScope.websiteSections(
    sites: List<SiteItem>,
    collections: List<CollectionItem>,
    browsers: InstalledBrowsers?,
    query: String,
    collectionTitle: (WebsiteCollection) -> String,
    settingsLocked: Boolean,
    advancedExpanded: Boolean,
    onToggleAdvanced: () -> Unit,
    onAddWebsite: (String) -> Boolean,
    onToggleWebsite: (String) -> Unit,
    copyButton: @Composable () -> Unit,
) {
    // with no browser to read, nothing else here does anything, so that comes first
    val noReadableBrowser = browsers != null && browsers.readable.isEmpty()
    if (noReadableBrowser) {
        item(key = "browsers") { BrowsersNote(browsers!!) }
    }
    item(key = "addWebsite") {
        AddWebsiteField(enabled = !settingsLocked, onAdd = onAddWebsite)
    }
    item(key = "sitesHeader") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppListSectionHeader(stringResource(id = R.string.websites_section_yours))
            copyButton()
        }
    }
    val shownSites = sites.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    if (sites.isEmpty()) {
        item(key = "sitesEmpty") {
            Text(
                text = stringResource(id = R.string.websites_section_yours_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    } else {
        items(shownSites, key = { it.entry }) { site ->
            WebsiteListItem(
                entry = site.entry,
                title = site.name,
                supportingText = null,
                icon = Icons.Default.Language,
                checked = site.checked,
                enabled = !settingsLocked,
                onToggle = onToggleWebsite,
                modifier = Modifier.animateItem()
            )
        }
    }
    item(key = "collectionsHeader") {
        AppListSectionHeader(stringResource(id = R.string.websites_section_collections))
    }
    fun collectionItems(shown: List<CollectionItem>) = items(shown, key = { it.collection.id }) {
        WebsiteListItem(
            entry = Websites.collectionEntry(it.collection),
            title = stringResource(id = it.collection.title),
            supportingText = it.description(),
            icon = it.collection.icon,
            checked = it.checked,
            enabled = !settingsLocked,
            onToggle = onToggleWebsite,
            modifier = Modifier.animateItem()
        )
    }
    if (query.isNotBlank()) {
        collectionItems(collections.filter { it.matches(query, collectionTitle(it.collection)) })
    } else {
        val (everyday, advanced) = collections.partition { !it.collection.advanced }
        collectionItems(everyday)
        if (advanced.isNotEmpty()) {
            item(key = "advancedCollections") {
                AdvancedCollectionsRow(
                    summary = advanced.joinToString { collectionTitle(it.collection) },
                    expanded = advancedExpanded,
                    onToggle = onToggleAdvanced
                )
            }
            if (advancedExpanded) collectionItems(advanced)
        }
    }
    if (browsers != null && !noReadableBrowser) {
        item(key = "browsers") { BrowsersNote(browsers) }
    }
}

/**
 * Takes a site the way people have one at hand: typed from memory, or pasted from the browser with
 * "https://www." and a path. A path narrows the entry to that part of the site.
 */
@Composable
private fun AddWebsiteField(enabled: Boolean, onAdd: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        if (text.isBlank()) return
        if (onAdd(text)) {
            text = ""
            invalid = false
        } else {
            invalid = true
        }
    }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            invalid = false
        },
        enabled = enabled,
        singleLine = true,
        label = { Text(stringResource(id = R.string.websites_add_label)) },
        placeholder = { Text(stringResource(id = R.string.websites_add_placeholder)) },
        isError = invalid,
        supportingText = if (invalid) {
            { Text(stringResource(id = R.string.websites_add_invalid)) }
        } else null,
        trailingIcon = {
            IconButton(onClick = ::submit, enabled = enabled && text.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.websites_add_action)
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/**
 * A site or a collection, in the same row the apps use, with an icon where an app has its own.
 */
@Composable
private fun WebsiteListItem(
    entry: String,
    title: String,
    supportingText: String?,
    icon: ImageVector,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AppSelectionListItem(
        packageName = entry,
        appName = title,
        appCategory = "",
        isSystemApp = false,
        checked = checked,
        modifier = modifier,
        enabled = enabled,
        showAppIcon = false,
        showTags = false,
        supportingText = supportingText,
        extraLeadingContent = {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        onCheckedChange = { onToggle(entry) }
    )
}

/**
 * Folds the [WebsiteCollection.advanced] collections away, worded like the "Advanced" row of the
 * settings screens and lined up with the collection rows around it. Opening it only shows them, so
 * it works while the settings are locked.
 */
@Composable
private fun AdvancedCollectionsRow(summary: String, expanded: Boolean, onToggle: () -> Unit) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f, label = "advancedCollectionsChevron"
    )
    ListItem(
        headlineContent = { Text(text = stringResource(id = R.string.feature_settings_advanced)) },
        supportingContent = {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        // as wide as the checkboxes above, so the chevron sits in their column
        trailingContent = {
            Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.rotate(chevronRotation)
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onToggle)
    )
}

/**
 * Which of the phone's browsers DetoxDroid can read, and what it cannot see anywhere. Without a
 * browser it can read this is a warning at the top, since nothing on this side would work.
 */
@Composable
private fun BrowsersNote(browsers: InstalledBrowsers) {
    val locale = LocalConfiguration.current.locales[0]
    fun joined(names: List<String>): String = ListFormatter.getInstance(locale).format(names)
    val lines = buildList {
        if (browsers.readable.isEmpty()) {
            add(stringResource(id = R.string.websites_browsers_none))
        } else {
            add(stringResource(id = R.string.websites_browsers_readable, joined(browsers.readable)))
        }
        if (browsers.unreadable.isNotEmpty()) {
            add(stringResource(id = R.string.websites_browsers_unreadable, joined(browsers.unreadable)))
        }
        add(stringResource(id = R.string.websites_inAppBrowsers))
    }
    if (browsers.readable.isEmpty()) {
        IconCard(icon = Icons.Default.Warning) {
            lines.forEach { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
        }
        return
    }
    Row(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 2.dp, end = 12.dp)
                .size(18.dp)
        )
        Text(
            text = lines.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * What a collection row says under its name: the first sites of a list we wrote, or the size and
 * source of a large one. Null while it is loading.
 */
@Composable
private fun CollectionItem.description(): String? = when {
    size == 0 -> null
    collection.curated -> stringResource(
        id = R.string.websites_collection_sample,
        sample.joinToString(", "),
        size - sample.size
    )

    // the app's language, which can differ from the phone's
    else -> stringResource(
        id = R.string.websites_collection_size,
        NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(size)
    )
}

/** Whether the search [query] finds this collection, by its [title] or one of the sites it names. */
private fun CollectionItem.matches(query: String, title: String): Boolean =
    title.contains(query, ignoreCase = true) || sample.any { it.contains(query, ignoreCase = true) }

private val WebsiteCollection.icon: ImageVector
    get() = when (this) {
        WebsiteCollection.SOCIAL -> Icons.Default.Forum
        WebsiteCollection.VIDEO -> Icons.Default.SmartDisplay
        WebsiteCollection.ADULT -> Icons.Default.NoAdultContent
        WebsiteCollection.GAMBLING -> Icons.Default.Casino
    }
