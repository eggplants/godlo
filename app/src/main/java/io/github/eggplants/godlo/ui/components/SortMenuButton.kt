package io.github.eggplants.godlo.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.eggplants.godlo.R
import io.github.eggplants.godlo.core.LibrarySort
import io.github.eggplants.godlo.core.SortKey

/**
 * A top app bar action opening a menu of what to sort by, out of [keys], and which way. The menu
 * stays open, so both can be picked in one go.
 */
@Composable
fun SortMenuButton(current: LibrarySort, keys: List<SortKey>, onSelect: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription =
                    stringResource(R.string.sort_button, stringResource(current.key.label)),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            keys.forEach { key ->
                SortMenuItem(stringResource(key.label), key == current.key) {
                    onSelect(current.copy(key = key))
                }
            }
            HorizontalDivider()
            SortMenuItem(stringResource(R.string.sort_ascending), !current.descending) {
                onSelect(current.copy(descending = false))
            }
            SortMenuItem(stringResource(R.string.sort_descending), current.descending) {
                onSelect(current.copy(descending = true))
            }
        }
    }
}

@Composable
private fun SortMenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text) },
        trailingIcon = {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = stringResource(R.string.layout_selected),
                )
            }
        },
        onClick = onClick,
    )
}
