package com.musheer360.swiftslate.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musheer360.swiftslate.R
import com.musheer360.swiftslate.ui.theme.BellPalette

/** One round color sample. The picked one wears a check mark. */
@Composable
private fun Swatch(palette: BellPalette, selected: Boolean, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(palette.swatch)
            // The outline keeps the near-black Classic swatch visible on the dark background.
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = palette.displayName
                this.selected = selected
            },
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = if (palette.swatch.luminance() > 0.4f) Color.Black else Color.White,
                modifier = Modifier.size(size * 0.6f)
            )
        }
    }
}

/**
 * A single compact row of swatches for Settings: the label and one tap-to-pick circle per
 * palette. No color names, just the colors.
 */
@Composable
fun PaletteSwatchRow(selected: BellPalette, onPick: (BellPalette) -> Unit) {
    val rhythm = LocalSlateRhythm.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.colors_title),
            fontSize = rhythm.bodySize,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(rhythm.formGap))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BellPalette.entries.forEach { palette ->
                Swatch(palette, palette == selected, 32.dp) { onPick(palette) }
            }
        }
    }
}

/**
 * Shown once on a fresh install. Whatever is highlighted when the dialog closes (Classic if
 * nothing was tapped) is saved, so it never comes back.
 */
@Composable
fun FirstLaunchColorPicker(onPick: (BellPalette) -> Unit) {
    var highlighted by remember { mutableStateOf(BellPalette.CLASSIC) }
    AlertDialog(
        onDismissRequest = { onPick(highlighted) },
        title = { Text(stringResource(R.string.colors_welcome_title)) },
        text = {
            Column {
                Text(stringResource(R.string.colors_welcome_desc))
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BellPalette.entries.forEach { palette ->
                        Swatch(palette, palette == highlighted, 36.dp) { highlighted = palette }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(highlighted) }) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
