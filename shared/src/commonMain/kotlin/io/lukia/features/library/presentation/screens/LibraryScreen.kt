package io.lukia.features.library.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lukia.features.import.domain.CoverImage
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.presentation.rememberPlatformFilePicker
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.displayTitle
import io.lukia.features.library.presentation.displayType
import io.lukia.features.library.presentation.formattedSize
import lukia.shared.generated.resources.Res
import lukia.shared.generated.resources.library_24px
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = koinViewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val filePicker = rememberPlatformFilePicker()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrorMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Library") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Import") },
                icon = {
                    Icon(
                        painter = painterResource(Res.drawable.library_24px),
                        contentDescription = null
                    )
                },
                onClick = {
                    filePicker.launch { pickedDocument ->
                        pickedDocument?.let { viewModel.importDocument(it) }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (documents.isEmpty()) {
            LibraryEmptyState(modifier = Modifier.padding(paddingValues))
        } else {
            LibraryGrid(
                documents = documents,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}

@Composable
private fun LibraryGrid(
    documents: List<DocumentInfo>,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(documents) { document ->
            BookCard(document)
        }
    }
}

@Composable
private fun BookCard(document: DocumentInfo) {
    val coverBitmap = rememberCoverBitmap(document.coverImage)

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f),
            shape = RoundedCornerShape(12.dp),
            color = bookCoverColor(document.displayTitle())
        ) {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(Res.drawable.library_24px),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        Text(
            text = document.displayTitle(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )

        Text(
            text = "${document.displayType()} · ${document.formattedSize()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!document.exists) {
            Text(
                text = "Unavailable",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun LibraryEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(Res.drawable.library_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            Text(
                text = "Import a PDF or EPUB to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun rememberCoverBitmap(coverImage: CoverImage?): ImageBitmap? {
    return remember(coverImage) {
        coverImage?.let { runCatching { it.bytes.decodeToImageBitmap() }.getOrNull() }
    }
}

private val bookCoverPalette = listOf(
    Color(0xFF6750A4),
    Color(0xFF386A20),
    Color(0xFFAD1457),
    Color(0xFF00696D),
    Color(0xFFB3261E),
    Color(0xFF9C4146)
)

private fun bookCoverColor(seed: String): Color {
    val hash = if (seed.hashCode() == Int.MIN_VALUE) 0 else kotlin.math.abs(seed.hashCode())
    return bookCoverPalette[hash % bookCoverPalette.size]
}
