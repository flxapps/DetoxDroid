package com.flx_apps.digitaldetox.ui.screens.premium

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flx_apps.digitaldetox.R
import com.flx_apps.digitaldetox.ui.widgets.CopyableText

/**
 * The Bitcoin address to donate to, to copy or to hand to a wallet app. It is shown first instead
 * of opening a bitcoin: link right away, since a phone without a wallet has nothing to open it.
 */
@Composable
internal fun BitcoinAddressDialog(address: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CurrencyBitcoin, contentDescription = null) },
        title = { Text(stringResource(R.string.about_bitcoin)) },
        text = {
            Column {
                Text(stringResource(R.string.about_bitcoin_message))
                CopyableText(
                    text = address,
                    copyLabel = stringResource(R.string.about_bitcoin_copy),
                    copiedMessage = stringResource(R.string.about_bitcoin_copied),
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("bitcoin:$address")))
                }.onFailure {
                    Toast.makeText(context, R.string.about_bitcoin_noWallet, Toast.LENGTH_LONG)
                        .show()
                }
            }) {
                Text(stringResource(R.string.about_bitcoin_openWallet))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
    )
}
