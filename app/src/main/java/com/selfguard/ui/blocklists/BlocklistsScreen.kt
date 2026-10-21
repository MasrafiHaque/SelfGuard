package com.selfguard.ui.blocklists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.selfguard.domain.model.BlockedKeyword
import com.selfguard.domain.model.BlockedSite
import com.selfguard.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
) {
    val sites by viewModel.sites.collectAsStateWithLifecycle()
    val keywords by viewModel.keywords.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocklist") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add rule")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Domains (${sites.size})") },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Keywords (${keywords.size})") },
                )
            }

            if (selectedTab == 0) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sites, key = { it.id }) { site ->
                        SiteRow(
                            site = site,
                            onToggle = { viewModel.toggleSite(site.id, it) },
                            onDelete = { viewModel.deleteSite(site.id) },
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(keywords, key = { it.id }) { keyword ->
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

    if (showAddDialog) {
        AddRuleDialog(
            isDomain = selectedTab == 0,
            onDismiss = { showAddDialog = false },
            onAdd = { value ->
                if (selectedTab == 0) viewModel.addSite(value) else viewModel.addKeyword(value)
                showAddDialog = false
            },
        )
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
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
