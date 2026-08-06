package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.library.presentation.LibraryViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel()
) {
    val documents by viewModel.documents.collectAsState()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column {
            Text("Library")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.importDocument() }) {
                Text("Import")
            }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                items(documents) { doc ->
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("File Name: ${doc.document.fileName}")
                        Text("Full Path: ${doc.document.absolutePath}")
                        Text("Type: ${doc.document.extension}")
                    }
                }
            }
        }
    }
}