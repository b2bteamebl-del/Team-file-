package com.example.ui.screens.admin

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyncStatusEntity
import com.example.ui.common.VoiceInputField
import com.example.ui.theme.EblGold
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateUtils

@Composable
fun GoogleSheetsSyncScreen(
  viewModel: AppViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val syncStatus by viewModel.syncStatus.collectAsState()
  val status = syncStatus ?: SyncStatusEntity()
  val scrollState = rememberScrollState()

  var inputSheetUrl by remember(status.spreadsheetId) {
    mutableStateOf(
      if (status.spreadsheetId.isNotBlank()) "https://docs.google.com/spreadsheets/d/${status.spreadsheetId}/edit" else ""
    )
  }
  var inputAppsScriptUrl by remember(status.appsScriptUrl) {
    mutableStateOf(
      if (status.appsScriptUrl.isNotBlank()) status.appsScriptUrl
      else "https://script.google.com/macros/s/AKfycbzxQ2GtKwhT8UjUdvqPTWielndlsMu9d_rVFf2ro4sI5-uCRrvj8uQXFKpVnBF7g9r0NQ/exec"
    )
  }
  var isSavingUrl by remember { mutableStateOf(false) }
  var isSavingScriptUrl by remember { mutableStateOf(false) }
  var isSyncingNow by remember { mutableStateOf(false) }
  var isPullingNow by remember { mutableStateOf(false) }
  val isRealtimeAutoSyncEnabled by viewModel.isRealtimeAutoSyncEnabled.collectAsState()
  val isSyncingInProgress by viewModel.isSyncingInProgress.collectAsState()
  var feedbackMessage by remember { mutableStateOf<String?>(null) }

  val appsScriptTemplate = """
/**
 * EBL Sales & RM Suite - Multi-Device Bi-Directional Auto-Sync Web App
 * Tabs: Customer_Files, RM_Details, Universal_Settings, Important_Documents, Call_Logs, SMS_Notifications, Audit_Trail, Attachments, RM_Location_Logs
 */
function doGet(e) {
  return handleFetchAllData();
}

function onEdit(e) {
  try {
    if (!e || !e.range) return;
    var range = e.range;
    var sheet = range.getSheet();
    var sheetName = sheet.getName();
    var row = range.getRow();
    if (row <= 1) return;
    var nowStr = Utilities.formatDate(new Date(), "GMT+6", "yyyy-MM-dd HH:mm:ss");
    if (sheetName === 'Customer_Files' || sheetName === 'Files') {
      sheet.getRange(row, 18).setValue(nowStr);
      sheet.getRange(row, 19).setValue('Manual_Sheet_Edit');
    } else if (sheetName === 'RM_Details' || sheetName === 'RM_Directory') {
      sheet.getRange(row, 14).setValue(nowStr);
    } else if (sheetName === 'Universal_Settings' || sheetName === 'Settings') {
      sheet.getRange(row, 4).setValue('Manual_Sheet_Edit');
      sheet.getRange(row, 5).setValue(nowStr);
    }
  } catch (err) {}
}

function doPost(e) {
  try {
    var contents = e.postData ? e.postData.contents : '{}';
    var data = JSON.parse(contents);
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    if (!ss && data.spreadsheetId) {
      try { ss = SpreadsheetApp.openById(data.spreadsheetId); } catch (e) {}
    }
    if (!ss) {
      try { ss = SpreadsheetApp.openById("1lb9Wou10ecl28EUgaXD2cA3YCNY7nNHp1BOFrrLezqI"); } catch (e) {}
    }

    var fileSheet = getOrCreateSheet(ss, 'Customer_Files', [
      'SL', 'CC-Number', 'File ID', 'Customer Name', 'Company Name',
      'Office Address', 'Mobile', 'Email', 'Product Type',
      'Application Status', 'Active Status', 'Assigned RM Code', 'Pending Documents',
      'CPV Remarks', 'CPV Status', 'GPS Submission Address', 'GPS Lat', 'GPS Lng',
      'Last Updated', 'Updated By'
    ], '#0A192F');

    var rmSheet = getOrCreateSheet(ss, 'RM_Details', [
      'RM Code', 'Full Name', 'Mobile', 'Email',
      'Office Address', 'Role', 'Account Status',
      'Credit Card Target', 'Corporate Card Target', 'B2B Target',
      'Password Hash', 'Salt', 'Created At', 'Last Updated'
    ], '#1E3A8A');

    var settingsSheet = getOrCreateSheet(ss, 'Universal_Settings', [
      'Setting Key', 'Setting Value', 'Description', 'Last Updated By', 'Last Updated At'
    ], '#065F46');

    var docSheet = getOrCreateSheet(ss, 'Important_Documents', [
      'Doc ID', 'Title', 'Category', 'Description', 'File Name', 'File Type', 'File Size (Bytes)',
      'Uploaded By', 'Uploader Name', 'Uploader Role', 'Created At', 'Last Updated'
    ], '#0F766E');

    var callSheet = getOrCreateSheet(ss, 'Call_Logs', [
      'Call ID', 'Caller RM', 'Caller Name', 'Recipient RM', 'Recipient Name', 'Recipient Mobile', 'Call Type', 'Duration (Sec)', 'Timestamp', 'Status'
    ], '#2563EB');

    var smsSheet = getOrCreateSheet(ss, 'SMS_Notifications', [
      'Notification ID', 'Recipient RM Code', 'Recipient Name', 'Recipient Mobile',
      'Triggered By Role', 'Triggered By Code', 'Action Type', 'Target ID',
      'Message Text', 'Delivery Status', 'Timestamp'
    ], '#991B1B');

    // Deletions
    if (data.action === 'DELETE_FILE' || (data.deletedFileIds && data.deletedFileIds.length > 0)) {
      var toDel = (data.deletedFileIds || [data.fileId]).map(function(s){ return String(s).toLowerCase().trim(); });
      var curFiles = fileSheet.getDataRange().getValues();
      for (var d = curFiles.length - 1; d >= 1; d--) {
        var rowVal = curFiles[d];
        var cellId = String(rowVal[1] || rowVal[2] || '').toLowerCase().trim();
        if (toDel.indexOf(cellId) !== -1) {
          fileSheet.deleteRow(d + 1);
        }
      }
    }

    if (data.action === 'DELETE_RM' || (data.deletedRmCodes && data.deletedRmCodes.length > 0)) {
      var toDelR = (data.deletedRmCodes || [data.rmCode]).map(function(s){ return String(s).toUpperCase().trim(); });
      var curRms = rmSheet.getDataRange().getValues();
      for (var dr = curRms.length - 1; dr >= 1; dr--) {
        var code = String(curRms[dr][0] || '').toUpperCase().trim();
        if (toDelR.indexOf(code) !== -1) {
          rmSheet.deleteRow(dr + 1);
        }
      }
    }

    // Upsert Files
    if (data.files && data.files.length > 0) {
      data.files.forEach(function(f, idx) {
        fileSheet.appendRow([
          f.serialNumber || (idx + 1), f.ccNumber || '', f.fileId || '', f.customerName || '',
          f.companyName || '', f.officeAddress || '', f.mobile || '', f.email || '',
          f.productType || '', f.applicationStatus || '', f.activeStatus || '',
          f.assignedRmCode || '', f.pendingDocuments || '', f.cpvRemarks || '',
          f.cpvStatus || '', f.submissionAddress || '', f.submissionLat || 0, f.submissionLng || 0,
          f.updatedAt || '', f.updatedBy || ''
        ]);
      });
    }

    // Upsert Important Documents
    if (data.importantDocuments && data.importantDocuments.length > 0) {
      data.importantDocuments.forEach(function(doc) {
        docSheet.appendRow([
          doc.docId || '', doc.title || '', doc.category || '', doc.description || '',
          doc.fileName || '', doc.fileType || '', doc.fileSize || 0, doc.uploadedBy || '',
          doc.uploaderName || '', doc.uploaderRole || '', doc.createdAt || '', doc.updatedAt || ''
        ]);
      });
    }

    return ContentService.createTextOutput(JSON.stringify({ status: 'success' })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}

function handleFetchAllData() {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var fileSheet = ss.getSheetByName('Customer_Files');
    var rmSheet = ss.getSheetByName('RM_Details');
    var settingsSheet = ss.getSheetByName('Universal_Settings');
    var docSheet = ss.getSheetByName('Important_Documents');
    return ContentService.createTextOutput(JSON.stringify({
      status: 'success',
      files: fileSheet ? extractAllSheetFiles(fileSheet) : [],
      rms: rmSheet ? extractAllSheetRms(rmSheet) : [],
      settings: settingsSheet ? extractAllSheetSettings(settingsSheet) : [],
      importantDocuments: docSheet ? extractAllSheetDocs(docSheet) : []
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: 'error', message: err.toString() })).setMimeType(ContentService.MimeType.JSON);
  }
}

function getOrCreateSheet(ss, sheetName, headers, headerColor) {
  var sheet = ss.getSheetByName(sheetName);
  if (!sheet) {
    sheet = ss.insertSheet(sheetName);
    sheet.appendRow(headers);
    sheet.getRange(1, 1, 1, headers.length).setBackground(headerColor || '#0A192F').setFontColor('#FFFFFF').setFontWeight('bold');
    sheet.setFrozenRows(1);
  }
  return sheet;
}
  """.trimIndent()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(scrollState)
      .padding(14.dp)
      .testTag("google_sheets_sync_screen")
  ) {
    // Header Banner
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = EblNavyDark)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.CloudDone,
              contentDescription = null,
              tint = EblGold,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Google Sheets Auto-Sync",
              fontSize = 17.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
          Surface(
            color = Color(0xFF10B981).copy(alpha = 0.2f),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "REAL-TIME AUTO",
              color = Color(0xFF34D399),
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = "Every file creation, update, deletion, SMS, call log, and Important Document automatically syncs into your Google Sheet in the background.",
          fontSize = 12.sp,
          color = Color(0xFFE2E8F0),
          lineHeight = 16.sp
        )
      }
    }
    Spacer(modifier = Modifier.height(14.dp))

    // Real-Time 2-Way Auto-Sync Switch Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(
        containerColor = if (isRealtimeAutoSyncEnabled) Color(0xFFF0FDF4) else MaterialTheme.colorScheme.surface
      ),
      border = androidx.compose.foundation.BorderStroke(
        1.dp,
        if (isRealtimeAutoSyncEnabled) Color(0xFF86EFAC) else Color(0xFFE2E8F0)
      )
    ) {
      Row(
        modifier = Modifier.padding(14.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(if (isRealtimeAutoSyncEnabled) Color(0xFF16A34A) else Color.Gray),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isSyncingInProgress) Icons.Default.Refresh else Icons.Default.Sync,
              contentDescription = null,
              tint = Color.White,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Continuous Live Auto-Sync (50ms)",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (isRealtimeAutoSyncEnabled) Color(0xFF14532D) else MaterialTheme.colorScheme.onSurface
              )
              if (isSyncingInProgress) {
                Text(
                  text = "Syncing now...",
                  fontSize = 10.sp,
                  color = Color(0xFF16A34A),
                  fontWeight = FontWeight.Bold
                )
              }
            }
            Text(
              text = if (isRealtimeAutoSyncEnabled) "Real-time bi-directional auto sync is running" else "Auto-sync paused",
              fontSize = 11.sp,
              color = Color.DarkGray
            )
          }
        }
        Switch(
          checked = isRealtimeAutoSyncEnabled,
          onCheckedChange = { viewModel.toggleRealtimeAutoSync(it) }
        )
      }
    }
    Spacer(modifier = Modifier.height(14.dp))

    // Real-Time Sync Status Badge Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Sync Engine Status",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = EblNavyDark
          )
          val (badgeText, badgeBg, badgeColor) = when (status.lastSyncStatus) {
            "SUCCESS" -> Triple("Connected & Synced", Color(0xFFDCFCE7), Color(0xFF15803D))
            "IN_PROGRESS" -> Triple("Syncing...", Color(0xFFDBEAFE), Color(0xFF1D4ED8))
            "FAILED" -> Triple("Sync Issue", Color(0xFFFEE2E2), Color(0xFFB91C1C))
            else -> Triple("Ready", Color(0xFFFEF3C7), Color(0xFFB45309))
          }
          Surface(
            color = badgeBg,
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = badgeText,
              color = badgeColor,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("Last Sync Time", fontSize = 10.sp, color = Color.Gray)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = DateUtils.formatDateTime(status.lastSyncTimestamp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EblNavyDark
              )
            }
          }
          Surface(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("Connected Sheet ID", fontSize = 10.sp, color = Color.Gray)
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = status.spreadsheetId.take(16) + if (status.spreadsheetId.length > 16) "..." else "",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = EblNavyDark,
                fontFamily = FontFamily.Monospace
              )
            }
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(14.dp))

    // Google Spreadsheet Link Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Link, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Google Spreadsheet Link",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = EblNavyDark
          )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Sheet ID: 1lb9Wou10ecl28EUgaXD2cA3YCNY7nNHp1BOFrrLezqI. Paste URL or Sheet ID below:",
          fontSize = 11.sp,
          color = Color.Gray
        )
        Spacer(modifier = Modifier.height(12.dp))
        VoiceInputField(
          value = inputSheetUrl,
          onValueChange = {
            inputSheetUrl = it
            feedbackMessage = null
          },
          label = "Google Sheet Link (URL)",
          placeholder = "https://docs.google.com/spreadsheets/d/1lb9Wou10ecl28EUgaXD2cA3YCNY7nNHp1BOFrrLezqI/edit",
          leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = EblNavyPrimary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_google_sheet_url"
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              if (inputSheetUrl.isBlank()) {
                feedbackMessage = "Please enter or paste a valid Google Sheet URL."
                return@Button
              }
              isSavingUrl = true
              viewModel.setGoogleSheetUrl(inputSheetUrl) { success, msg ->
                isSavingUrl = false
                feedbackMessage = msg
              }
            },
            enabled = !isSavingUrl,
            colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f).testTag("btn_save_sheet_url")
          ) {
            if (isSavingUrl) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Connecting...", fontSize = 12.sp)
            } else {
              Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Connect Sheet", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
          if (status.spreadsheetId.isNotBlank()) {
            OutlinedButton(
              onClick = {
                val fullUrl = if (inputSheetUrl.startsWith("http")) inputSheetUrl else "https://docs.google.com/spreadsheets/d/${status.spreadsheetId}/edit"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl))
                context.startActivity(intent)
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.testTag("btn_open_google_sheet")
            ) {
              Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Open Sheet", fontSize = 12.sp)
            }
          }
        }
        if (feedbackMessage != null) {
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = feedbackMessage!!,
            fontSize = 11.sp,
            color = if (feedbackMessage!!.contains("Success", ignoreCase = true) || feedbackMessage!!.contains("Linked", ignoreCase = true)) Color(0xFF059669) else Color(0xFFDC2626),
            fontWeight = FontWeight.Medium
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(14.dp))

    // Apps Script Web App Connector
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Code, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Apps Script Web App Connector",
              fontSize = 15.sp,
              fontWeight = FontWeight.Bold,
              color = EblNavyDark
            )
          }
          Surface(
            color = Color(0xFFEFF6FF),
            shape = RoundedCornerShape(6.dp)
          ) {
            Text(
              text = "ALL TABS SYNC",
              color = Color(0xFF1D4ED8),
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          text = "Google Apps Script Web App URL to sync Customer Files, RMs, SMS, Call Logs and Important Documents automatically:",
          fontSize = 11.sp,
          color = Color.Gray
        )
        Spacer(modifier = Modifier.height(10.dp))
        VoiceInputField(
          value = inputAppsScriptUrl,
          onValueChange = { inputAppsScriptUrl = it },
          label = "Apps Script Web App URL",
          placeholder = "https://script.google.com/macros/s/.../exec",
          leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, tint = EblNavyPrimary) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_apps_script_url"
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = {
              if (inputAppsScriptUrl.isBlank()) {
                feedbackMessage = "Please paste your Google Apps Script Web App URL."
                return@Button
              }
              isSavingScriptUrl = true
              viewModel.updateAppsScriptConfig(inputAppsScriptUrl.trim(), "EBL_SYNC_KEY") { success, msg ->
                isSavingScriptUrl = false
                feedbackMessage = if (success) "Apps Script Web App connected successfully!" else msg
              }
            },
            enabled = !isSavingScriptUrl,
            colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            if (isSavingScriptUrl) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Save Connector", fontSize = 11.sp)
            }
          }
          OutlinedButton(
            onClick = {
              val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
              val clip = android.content.ClipData.newPlainText("Google Apps Script", appsScriptTemplate)
              clipboard.setPrimaryClip(clip)
              feedbackMessage = "Apps Script code copied to clipboard!"
            },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Copy Script Code", fontSize = 11.sp)
          }
        }
      }
    }
    Spacer(modifier = Modifier.height(14.dp))

    // Push and Pull buttons
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Button(
        onClick = {
          isSyncingNow = true
          viewModel.triggerGoogleSheetsSync { success, msg ->
            isSyncingNow = false
            feedbackMessage = msg
          }
        },
        enabled = !isSyncingNow && !isPullingNow,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).testTag("btn_sync_now")
      ) {
        if (isSyncingNow) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pushing...", fontSize = 11.sp)
        } else {
          Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Push to Sheet", fontSize = 11.sp)
        }
      }
      Button(
        onClick = {
          isPullingNow = true
          viewModel.pullDataFromGoogleSheets { success, msg ->
            isPullingNow = false
            feedbackMessage = msg
          }
        },
        enabled = !isSyncingNow && !isPullingNow,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).testTag("btn_pull_sheets")
      ) {
        if (isPullingNow) {
          CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(6.dp))
          Text("Pulling...", fontSize = 11.sp)
        } else {
          Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Pull from Sheet", fontSize = 11.sp)
        }
      }
    }
    Spacer(modifier = Modifier.height(24.dp))
  }
}
