package com.selfguard.ui.blocklists

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfguard.data.local.InstalledApp
import com.selfguard.domain.model.BlockedApp
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import com.selfguard.ui.MainViewModel

private const val TAB_APPS = 0
private const val TAB_WEBSITES = 1
private const val TAB_KEYWORDS = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistsScreen(viewModel: MainViewModel) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    val blockedApps by viewModel.blockedApps.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val installedLoaded by viewModel.installedAppsLoaded.collectAsStateWithLifecycle()
    val protectedPackages by viewModel.protectedPackages.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_WEBSITES) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Blocklist") }) },
        floatingActionButton = {
            if (selectedTab != TAB_APPS) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add rule")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_APPS,
                    onClick = { selectedTab = TAB_APPS },
                    icon = { Icon(Icons.Default.Apps, contentDescription = null) },
                    text = { Text("Apps (${blockedApps.size})") },
                )
                Tab(
                    selected = selectedTab == TAB_WEBSITES,
                    onClick = { selectedTab = TAB_WEBSITES },
                    icon = { Icon(Icons.Default.Language, contentDescription = null) },
                    text = { Text("Sites (${sites.size})") },
                )
                Tab(
                    selected = selectedTab == TAB_KEYWORDS,
                    onClick = { selectedTab = TAB_KEYWORDS },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                    text = { Text("Words (${keywords.size})") },
                )
            }

            when (selectedTab) {
                TAB_APPS -> AppsTab(
                    blockedApps = blockedApps,
                    installedApps = installedApps,
                    loaded = installedLoaded,
                    protectedPackages = protectedPackages,
                    onToggle = viewModel::toggleApp,
                )

                TAB_WEBSITES -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sites, key = { "site-${it.id}" }) { site ->
                        SiteRow(
                            site = site,
                            onToggle = { viewModel.toggleSite(site.id, it) },
                            onDelete = { viewModel.deleteSite(site.id) },
                        )
                    }
                }

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(keywords, key = { "kw-${it.id}" }) { keyword ->
                        KeywordRow(
                            keyword = keyword,
                            onToggle = { viewModel.toggleKeyword(keyword.id, it) },
                            onDelete = { viewModel.deleteKeyword(keyword.id) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog && selectedTab != TAB_APPS) {
        AddRuleDialog(
            isDomain = selectedTab == TAB_WEBSITES,
            onDismiss = { showAddDialog = false },
            onAdd = { value ->
                if (selectedTab == TAB_WEBSITES) viewModel.addSite(value) else viewModel.addKeyword(value)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun AppsTab(
    blockedApps: List<BlockedApp>,
    installedApps: List<InstalledApp>,
    loaded: Boolean,
    protectedPackages: Set<String>,
    onToggle: (packageName: String, label: String, blocked: Boolean) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var hideSystem by rememberSaveable { mutableStateOf(true) }
    val blockedPackages = remember(blockedApps) { blockedApps.map { it.packageName }.toSet() }

    val filtered = remember(installedApps, query, hideSystem, protectedPackages) {
        installedApps
            .filter { it.packageName !in protectedPackages }
            .filter { !hideSystem || !it.isSystem }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search apps") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (blockedApps.isNotEmpty()) {
                item { SectionHeader("On your list (${blockedApps.size})") }
                items(blockedApps, key = { "blocked-${it.id}" }) { app ->
                    BlockedAppRow(app = app, onRemove = { onToggle(app.packageName, app.label, false) })
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Installed apps",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Hide system",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = hideSystem, onCheckedChange = { hideSystem = it })
                }
            }

            if (!loaded) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                items(filtered, key = { "app-${it.packageName}" }) { app ->
                    InstalledAppRow(
                        app = app,
                        blocked = app.packageName in blockedPackages,
                        onToggle = { onToggle(app.packageName, app.label, it) },
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Text(
                            text = "No apps match the filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun BlockedAppRow(
    app: BlockedApp,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label.ifBlank { app.packageName })
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = true, onCheckedChange = { if (!it) onRemove() })
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove from blocklist")
        }
    }
}

@Composable
private fun InstalledAppRow(
    app: InstalledApp,
    blocked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(
                bitmap = app.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = blocked, onCheckedChange = onToggle)
    }
}

@Composable
private fun SiteRow(
    site: BlockedSite,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = site.domain)
            Text(
                text = if (site.includeSubdomains) {
                    "also blocks subdomains"
                } else {
                    "exact host only"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = site.isEnabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun KeywordRow(
    keyword: BlockedKeyword,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = keyword.keyword)
            Text(
                text = if (keyword.matchInPageText) {
                    "matched in page text"
                } else {
                    "address bar only"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = keyword.isEnabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete")
        }
    }
}

@Composable
private fun AddRuleDialog(
    isDomain: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isDomain) "Block a domain" else "Block a keyword") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    label = {
                        Text(if (isDomain) "example.com" else "word to block")
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isDomain) {
                        "Subdomains are blocked together with the domain."
                    } else {
                        "Keywords are matched in the address bar, the page title and the page text."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(value.trim()) },
                enabled = value.isNotBlank(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(4.dp))
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
