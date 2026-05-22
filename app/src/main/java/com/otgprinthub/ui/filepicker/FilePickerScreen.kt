package com.otgprinthub.ui.filepicker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.otgprinthub.domain.model.FileType
import com.otgprinthub.ui.navigation.Screen
import java.net.URLEncoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilePickerScreen(navController: NavController) {
    val allTypes = arrayOf("application/pdf", "image/*", "text/*")

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val encoded = URLEncoder.encode(it.toString(), "UTF-8")
            // Detect type from uri
            navController.navigate(Screen.PrintPreview.createRoute(encoded, "UNKNOWN"))
        }
    }

    val fileTypes = listOf(
        Triple("PDF Documents", Icons.Default.PictureAsPdf, arrayOf("application/pdf")),
        Triple("Images", Icons.Default.Image, arrayOf("image/*")),
        Triple("Text Files", Icons.Default.TextFields, arrayOf("text/*")),
        Triple("All Files", Icons.Default.FolderOpen, allTypes)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select File") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Select File Type", style = MaterialTheme.typography.titleMedium)

            fileTypes.forEach { (label, icon, mimeTypes) ->
                Card(
                    onClick = { fileLauncher.launch(mimeTypes) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
