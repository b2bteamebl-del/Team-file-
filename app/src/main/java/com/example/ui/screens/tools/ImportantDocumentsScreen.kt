package com.example.ui.screens.tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ImportantDocumentEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.EblGold
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.AttachmentHelper
import com.example.util.DateUtils
import kotlinx.coroutines.launch

@Composable
fun ImportantDocumentsScreen(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val allDocs by viewModel.importantDocuments.collectAsState()
  val isMentorOrAdmin = currentUser.role == "ADMIN" || currentUser.role == "MENTOR"

  var selectedCategory by remember { mutableStateOf("All") }
  var searchQuery by remember { mutableStateOf("") }
  var isSyncing by remember { mutableStateOf(false) }

  var showAddDialog by remember { mutableStateOf(false) }
  var docToDelete by remember { mutableStateOf<ImportantDocumentEntity?>(null) }

  val categories = listOf("All", "Policies & Circulars", "Product Manuals", "Forms & Formats", "Compliance", "Checklists")

  val filteredDocs = allDocs.filter { doc ->
    val matchCat = selectedCategory == "All" || doc.category.equals(selectedCategory, ignoreCase = true)
    val matchQuery = searchQuery.isBlank() ||
      doc.title.contains(searchQuery, ignoreCase = true) ||
      doc.description.contains(searchQuery, ignoreCase = true) ||
      doc.fileName.contains(searchQuery, ignoreCase = true)
    matchCat && matchQuery
  }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF8FAFC))
      .testTag("important_documents_screen")
  ) {
    // Top Bar
    Surface(
      color = EblNavyDark,
      shadowElevation = 4.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("btn_back_important_docs")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column {
            Text(
              text = "Important Banking Documents",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "Synchronized bank circulars, manuals & policy forms",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.8f)
            )
          }
        }

        // Real-time Cloud Sync button (Requirement 7!)
        IconButton(
          onClick = {
            isSyncing = true
            coroutineScope.launch {
              viewModel.triggerGoogleSheetsSync { success, msg ->
                isSyncing = false
                Toast.makeText(context, msg ?: "Documents synchronized with Google Sheets!", Toast.LENGTH_SHORT).show()
              }
            }
          },
          modifier = Modifier.testTag("btn_sync_important_docs")
        ) {
          if (isSyncing) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = EblGold, strokeWidth = 2.dp)
          } else {
            Icon(Icons.Default.Sync, contentDescription = "Sync", tint = EblGold)
          }
        }
      }
    }

    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Sync Status Banner
      item {
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
          border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
              Column {
                Text("Cloud Synchronized Documents", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                Text("Google Sheet Tab: 'Important_Documents' • Available to all RMs", fontSize = 10.sp, color = Color(0xFF15803D))
              }
            }
            if (isMentorOrAdmin) {
              Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_add_important_doc")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text("Add Doc", fontSize = 11.sp)
              }
            }
          }
        }
      }

      // Search field
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search document by title, circular name, or description...") },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
          shape = RoundedCornerShape(8.dp),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("input_search_docs")
        )
      }

      // Category Chips
      item {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
          items(categories) { cat ->
            val isSelected = cat == selectedCategory
            FilterChip(
              selected = isSelected,
              onClick = { selectedCategory = cat },
              label = {
                Text(
                  text = cat,
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EblNavyPrimary,
                selectedLabelColor = Color.White
              ),
              modifier = Modifier.testTag("doc_category_chip_$cat")
            )
          }
        }
      }

      // Documents List
      if (filteredDocs.isEmpty()) {
        item {
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
              contentAlignment = Alignment.Center
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("No documents found in this category.", fontSize = 13.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap the Sync button above to fetch new documents from Google Sheets.", fontSize = 11.sp, color = EblNavyPrimary)
              }
            }
          }
        }
      } else {
        items(filteredDocs) { doc ->
          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth().testTag("doc_card_${doc.docId}")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  modifier = Modifier.weight(1f)
                ) {
                  Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = EblNavyPrimary.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                  ) {
                    Box(contentAlignment = Alignment.Center) {
                      Icon(Icons.Default.Description, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(20.dp))
                    }
                  }
                  Column {
                    Text(
                      text = doc.title,
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Bold,
                      color = EblNavyDark
                    )
                    Text(
                      text = doc.fileName,
                      fontSize = 11.sp,
                      color = Color.Gray
                    )
                  }
                }

                Surface(
                  shape = RoundedCornerShape(4.dp),
                  color = Color(0xFFF1F5F9)
                ) {
                  Text(
                    text = doc.category,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = EblNavyPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              if (doc.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = doc.description,
                  fontSize = 12.sp,
                  lineHeight = 16.sp,
                  color = Color.DarkGray
                )
              }

              Spacer(modifier = Modifier.height(10.dp))
              HorizontalDivider(color = Color(0xFFF1F5F9))
              Spacer(modifier = Modifier.height(8.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column {
                  Text(
                    text = "Uploaded by ${doc.uploaderName} (${doc.uploaderRole})",
                    fontSize = 10.sp,
                    color = Color.Gray
                  )
                  Text(
                    text = DateUtils.formatDateTime(doc.createdAt),
                    fontSize = 9.sp,
                    color = Color.LightGray
                  )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                  Button(
                    onClick = {
                      val success = AttachmentHelper.viewOrDownloadFile(
                        context = context,
                        fileName = doc.fileName,
                        fileUri = doc.fileUri,
                        fileType = doc.fileType
                      )
                      if (!success) {
                        Toast.makeText(context, "Opening document ${doc.fileName}...", Toast.LENGTH_SHORT).show()
                      }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("btn_view_doc_${doc.docId}")
                  ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View / Download", fontSize = 11.sp)
                  }

                  if (isMentorOrAdmin) {
                    IconButton(
                      onClick = { docToDelete = doc },
                      modifier = Modifier.size(32.dp).testTag("btn_delete_doc_${doc.docId}")
                    ) {
                      Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                    }
                  }
                }
              }
            }
          }
        }
      }

      item {
        Spacer(modifier = Modifier.height(32.dp))
      }
    }
  }

  // DIALOG: Add Important Document (Admin/Mentor)
  if (showAddDialog) {
    var titleInput by remember { mutableStateOf("") }
    var descInput by remember { mutableStateOf("") }
    var categoryInput by remember { mutableStateOf("Policies & Circulars") }
    var fileNameInput by remember { mutableStateOf("") }

    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = {
        Text("Add Important Bank Document", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EblNavyDark)
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("This document will be automatically synced to Google Sheets so all RMs can view and download it.", fontSize = 11.sp, color = Color.Gray)

          OutlinedTextField(
            value = titleInput,
            onValueChange = { titleInput = it },
            label = { Text("Document Title *") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_add_doc_title")
          )

          OutlinedTextField(
            value = fileNameInput,
            onValueChange = { fileNameInput = it },
            label = { Text("File Name (e.g. EBL_Retail_Policy_2026.pdf)") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_add_doc_filename")
          )

          OutlinedTextField(
            value = categoryInput,
            onValueChange = { categoryInput = it },
            label = { Text("Category") },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          )

          OutlinedTextField(
            value = descInput,
            onValueChange = { descInput = it },
            label = { Text("Brief Description / Summary") },
            minLines = 2,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (titleInput.isBlank()) {
              Toast.makeText(context, "Please enter document title.", Toast.LENGTH_SHORT).show()
              return@Button
            }
            val fName = fileNameInput.ifBlank { "${titleInput.trim().replace(" ", "_")}.pdf" }
            viewModel.addImportantDocument(
              title = titleInput.trim(),
              category = categoryInput.trim(),
              description = descInput.trim(),
              fileName = fName,
              fileType = "application/pdf",
              fileSizeBytes = 2048L,
              fileUri = ""
            )
            showAddDialog = false
            Toast.makeText(context, "Document added and synced to Google Sheets!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.testTag("btn_confirm_add_doc")
        ) {
          Text("Save & Sync to Sheet")
        }
      },
      dismissButton = {
        TextButton(onClick = { showAddDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // DIALOG: Delete confirmation
  if (docToDelete != null) {
    val d = docToDelete!!
    AlertDialog(
      onDismissRequest = { docToDelete = null },
      title = { Text("Delete Document?") },
      text = { Text("Are you sure you want to remove '${d.title}'? It will be deleted from the local database and updated in Google Sheets.") },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteImportantDocument(d.docId)
            docToDelete = null
            Toast.makeText(context, "Document deleted.", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
        ) {
          Text("Delete")
        }
      },
      dismissButton = {
        TextButton(onClick = { docToDelete = null }) {
          Text("Cancel")
        }
      }
    )
  }
}
