package com.flx_apps.digitaldetox

import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.dci.dev.appinfobadge.AppInfoBadge
import com.dci.dev.appinfobadge.InfoItemWithLink

class AboutFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appInfoBadgeFragment = AppInfoBadge
            .headerColor { ContextCompat.getColor(requireContext(), R.color.colorPrimaryDark) }
            .withAppIcon { true }
            .withCustomItems { listOf(
                InfoItemWithLink(
                    iconId = R.drawable.ic_coffee,
                    title = getString(R.string.about_coffee),
                    link = Uri.parse(getString(R.string.about_coffee_link))
                ),
                InfoItemWithLink(
                    iconId = R.drawable.ic_patron,
                    title = getString(R.string.about_patron),
                    link = Uri.parse(getString(R.string.about_patron_link))
                ),
                InfoItemWithLink(
                    iconId = R.drawable.ic_contact_mail_3,
                    title = getString(R.string.about_contact),
                    link = Uri.parse(getString(R.string.about_contact_link))
                ),
                InfoItemWithLink(
                    iconId = R.drawable.ic_contact_site_github,
                    title = getString(R.string.about_github),
                    link = Uri.parse(getString(R.string.about_github_link))
                )
            )}
            .withPermissions { false }
            .withRater { false }
            .withChangelog { false }
            .withLibraries { false }
            .withLicenses { false }
            .show()
        parentFragmentManager.beginTransaction().replace(R.id.nav_host_fragment, appInfoBadgeFragment).commit()
    }
}