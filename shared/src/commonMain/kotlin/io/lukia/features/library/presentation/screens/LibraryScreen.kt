package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.lukia.features.import.presentation.ImportViewModel
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    viewModel: ImportViewModel = koinViewModel()
) {
    val selectedDocument by viewModel.documentInfo.collectAsState()

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Column {
            Text("Library")
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.importDocument() }) {
                Text("Import")
            }
            
            selectedDocument?.let { doc ->
                Spacer(modifier = Modifier.height(16.dp))
                Text("File Name: ${doc.document.fileName}")
                Text("Extension: ${doc.document.extension}")
                Text("Path: ${doc.document.absolutePath}")
            }
        }
    }
}