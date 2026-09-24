package io.github.eggplants.godlo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.LibraryLayout

val LibraryLayout.icon: ImageVector
    get() = when (this) {
        LibraryLayout.LIST -> Icons.AutoMirrored.Outlined.ViewList
        LibraryLayout.LARGE_GRID -> Icons.Outlined.GridView
        LibraryLayout.SMALL_GRID -> Icons.Outlined.GridOn
    }

/** A top app bar action showing the current layout, opening a menu of the others. */
@Composable
fun LayoutMenuButton(current: LibraryLayout, onSelect: (LibraryLayout) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                current.icon,
                contentDescription = stringResource(
                    R.string.layout_button,
                    stringResource(current.label)
                )
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibraryLayout.entries.forEach { layout ->
                DropdownMenuItem(
                    text = { Text(stringResource(layout.label)) },
                    leadingIcon = { Icon(layout.icon, contentDescription = null) },
                    trailingIcon = {
                        if (layout ==
                            current
                        ) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.layout_selected)
                            )
                        }
                    },
                    onClick = {
                        onSelect(layout)
                        open = false
                    }
                )
            }
        }
    }
}
