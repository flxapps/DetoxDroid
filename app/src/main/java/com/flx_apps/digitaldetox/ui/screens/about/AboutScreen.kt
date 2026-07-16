package com.flx_apps.digitaldetox.ui.screens.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.BuildConfig
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.premium.PremiumSheetController
import com.flx_apps.digitaldetox.ui.screens.nav_host.NavViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navViewModel: NavViewModel = NavViewModel.navViewModel()) {
    val uriHandler = LocalUriHandler.current
    val reportIssueLink = stringResource(id = R.string.about_reportIssue_link)
    val githubLink = stringResource(id = R.string.about_github_link)
    val contactLink = stringResource(id = R.string.about_contact_link)
    val coffeeLink = stringResource(id = R.string.about_coffee_link)
    val patronLink = stringResource(id = R.string.about_patron_link)
    val reportIssueTitle = stringResource(id = R.string.navigation_reportIssue)
    val githubTitle = stringResource(id = R.string.about_github)
    val contactTitle = stringResource(id = R.string.about_contact)
    val coffeeTitle = stringResource(id = R.string.about_coffee)
    val patronTitle = stringResource(id = R.string.about_patron)
    val contactSubtitle = stringResource(id = R.string.about_contact_subtitle)
    val coffeeSubtitle = stringResource(id = R.string.about_coffee_subtitle)
    val patronSubtitle = stringResource(id = R.string.about_patron_subtitle)
    val premiumTitle = stringResource(id = R.string.navigation_premium)
    val premiumSubtitle = stringResource(id = R.string.premium_tile_subtitle)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.navigation_about)) },
                navigationIcon = {
                    IconButton(onClick = { navViewModel.onBackPress() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_foreground_cropped),
                        contentDescription = null,
                        modifier = Modifier.size(84.dp)
                    )
                    Text(
                        text = stringResource(id = R.string.app_name_),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = stringResource(
                            id = R.string.about_version,
                            BuildConfig.VERSION_NAME
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(id = R.string.about_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            linkItem(
                icon = Icons.Default.WorkspacePremium,
                title = premiumTitle,
                subtitle = premiumSubtitle,
                onClick = { PremiumSheetController.show() }
            )
            linkItem(
                icon = Icons.Default.BugReport,
                title = reportIssueTitle,
                onClick = { uriHandler.openUri(reportIssueLink) }
            )
            linkItem(
                icon = Icons.Default.Code,
                title = githubTitle,
                onClick = { uriHandler.openUri(githubLink) }
            )
            linkItem(
                icon = Icons.Default.AlternateEmail,
                title = contactTitle,
                subtitle = contactSubtitle,
                onClick = { uriHandler.openUri(contactLink) }
            )
            linkItem(
                icon = Icons.Default.Favorite,
                title = coffeeTitle,
                subtitle = coffeeSubtitle,
                onClick = { uriHandler.openUri(coffeeLink) }
            )
            linkItem(
                icon = Icons.Default.VolunteerActivism,
                title = patronTitle,
                subtitle = patronSubtitle,
                onClick = { uriHandler.openUri(patronLink) }
            )
        }
    }
}

private fun LazyListScope.linkItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    item {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = subtitle?.let { { Text(it) } },
            leadingContent = { Icon(icon, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onClick)
        )
    }
}
