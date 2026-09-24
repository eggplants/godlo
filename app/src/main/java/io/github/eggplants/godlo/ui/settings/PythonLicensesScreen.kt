package io.github.eggplants.godlo.ui.settings

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.eggplants.godlo.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One entry of python_licenses.json, which native/licenses/python_licenses.py writes. */
@Serializable
data class PythonLicense(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    val text: String
)

private fun loadLicenses(context: Context): List<PythonLicense> = runCatching {
    context.assets.open("python_licenses.json").use { input ->
        Json.decodeFromString<List<PythonLicense>>(input.readBytes().decodeToString())
    }
}.getOrDefault(emptyList())

/** What the OSS Licenses plugin's list leaves out: the Python packages, bundled programs, ... */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PythonLicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val licenses by produceState(emptyList<PythonLicense>()) {
        value = withContext(Dispatchers.IO) { loadLicenses(context) }
    }
    var shown by remember { mutableStateOf<PythonLicense?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                title = { Text(stringResource(R.string.about_licenses_python)) },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 16.dp)
        ) {
            items(licenses, key = { it.name }) { entry ->
                ListItem(
                    headlineContent = {
                        Text(
                            listOf(entry.name, entry.version).filter {
                                it.isNotBlank()
                            }.joinToString(" ")
                        )
                    },
                    supportingContent = { Text(entry.license) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { shown = entry }
                )
            }
        }
    }

    shown?.let { entry ->
        val uriHandler = LocalUriHandler.current
        ModalBottomSheet(onDismissRequest = { shown = null }) {
            Column(
                Modifier.navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    entry.license,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (entry.url.isNotBlank()) {
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(entry.url) },
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        Text(stringResource(R.string.open_website), Modifier.padding(start = 8.dp))
                    }
                }
                if (entry.text.isNotBlank()) {
                    Text(
                        entry.text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            }
        }
    }
}
