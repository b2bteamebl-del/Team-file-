package com.example.ui.screens.admin

import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.fragment.app.FragmentActivity
import com.example.data.model.UserEntity
import com.example.ui.common.VoiceInputField
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.BiometricHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  modifier: Modifier = Modifier
) {
  val settings by viewModel.appSettings.collectAsState()
  val context = LocalContext.current
  val scrollState = rememberScrollState()

  val productSetting = settings.find { it.settingKey == "PRODUCT_TYPES" }?.settingValue
    ?: "Credit Card,B2B,Corporate Card,Split,Limit Enhancement"
  val pendingDocsSetting = settings.find { it.settingKey == "PENDING_DOCS_LIST" }?.settingValue
    ?: "NID,TIN,Office ID,Salary Certificate,Account Statement (6 Months),BIN,Trade License 2024-25,Trade License 2025-26,Trade License 2026-27,Loan Certificate,Card Statement (Month),Card Copy"
  val weekStartSetting = settings.find { it.settingKey == "WEEK_START_DAY" }?.settingValue ?: "SATURDAY"

  var productsInput by remember(productSetting) { mutableStateOf(productSetting) }
  var pendingDocsInput by remember(pendingDocsSetting) { mutableStateOf(pendingDocsSetting) }
  var weekStartInput by remember(weekStartSetting) { mutableStateOf(weekStartSetting) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .verticalScroll(scrollState)
      .padding(16.dp)
      .testTag("app_settings_screen")
  ) {
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = EblNavyDark)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = "System Configuration & Dropdowns",
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = "Manage global lists, reporting rules, and checklist items",
            fontSize = 11.sp,
            color = Color.LightGray
          )
        }
      }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Product Types
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Product Types (Comma Separated)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Options available to RMs in the customer file entry form", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        VoiceInputField(
          value = productsInput,
          onValueChange = { productsInput = it },
          label = "Product Types",
          singleLine = false,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_setting_products"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = { viewModel.updateSetting("PRODUCT_TYPES", productsInput.trim()) },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.align(Alignment.End)
        ) {
          Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save Products")
        }
      }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Pending Documents List
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Pending Documents Checklist", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("Checklist items available during customer file entry", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        VoiceInputField(
          value = pendingDocsInput,
          onValueChange = { pendingDocsInput = it },
          label = "Documents Checklist",
          singleLine = false,
          maxLines = 5,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_setting_pending_docs"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = {
            viewModel.updateSetting("PENDING_DOCS_LIST", pendingDocsInput.trim())
            Toast.makeText(context, "Pending documents checklist saved & synced!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.align(Alignment.End)
        ) {
          Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save Documents")
        }
      }
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Checklist Sender Items Management
    val defaultChecklistPresets = listOf(
      Triple("PENDING_DOCS_LIST", "Pending Docs Checklist", "NID\nTIN\nOffice ID\nSalary Certificate\nAccount Statement (6 Months)\nBIN\nTrade License 2024-25\nTrade License 2025-26\nTrade License 2026-27\nLoan Certificate\nCard Statement (Month)\nCard Copy"),
      Triple("CHECKLIST_ENHANCE_DOCS", "Limit Enhancement Docs", "Front & back photocopy of existing Credit Card\nLatest 6-month Salary / Business Bank Account Statement (sealed)\nLatest Salary Certificate / Original Pay Slips / Trade License renewal copy\nLatest E-TIN Certificate & Tax Return Assessment Acknowledgement Slip\nPhotocopy of National ID Card (NID) / Smart Card"),
      Triple("CHECKLIST_CORP_COMPANY_DOCS", "Corporate Card (Company)", "Valid Trade License (last 3-5 years renewal copies)\nMemorandum & Articles of Association (MOA & AOA) / Partnership Deed\nBoard Resolution authorizing Corporate Card facility & authorized signatories\nLatest 2 consecutive years Audited Financial Statements & Balance Sheet\nForm XII / Schedule X / List of Directors certified copy\nCompany E-TIN Certificate & BIN / VAT Registration Certificate\n12-Month Company Bank Account Statement (with bank seal & signature)"),
      Triple("CHECKLIST_CORP_EMPLOYEE_DOCS", "Corporate Card (Employee)", "Applicant Employee NID / Smart Card / Valid Passport photocopy\nEmployee Office ID Card photocopy & Business Visiting Card\nLetter of Introduction (LOI) / Corporate Card authorization on official company letterhead\n2 copies recent Passport size lab-print photographs of applicant\nApplicant Employee E-TIN Certificate photocopy\nLatest 6-Month Salary Account Bank Statement"),
      Triple("CHECKLIST_SALARIED_DOCS", "Salaried Credit Card", "NID / Smart Card / Valid Passport photocopy\n2 copies recent Passport size photographs\nLatest E-TIN Certificate & Tax Return Acknowledgment Slip\nLatest Salary Certificate / Original Pay Slips (last 3 months)\n6-month Salary Account Statement (with bank seal & signature)\nOffice ID Card photocopy & Visiting Card\nUtility Bill photocopy (Electricity / WASA / Gas residence)"),
      Triple("CHECKLIST_BUSINESS_DOCS", "Business Person Credit Card", "National ID Card (NID) photocopy\n2 copies recent Passport size photographs\nValid Trade License (last 3-5 years renewal copies)\n12-month Business & Personal Bank Account Statement (sealed)\nLatest E-TIN Certificate & Tax Return Acknowledgment Slip\nVisiting card & Memorandum of Association / Partnership Deed\nUtility bill of residence & business premises"),
      Triple("CHECKLIST_LOAN_DOCS", "Loan Application Docs", "National ID Card (NID) photocopy\n2 copies recent Passport size photographs\nLatest E-TIN Certificate & Tax Return Assessment Slip\nIncome proof (Salary Certificate / 6-12 month Bank Statement)\nOffice ID / Trade License photocopy\nUtility Bill photocopy (residence)"),
      Triple("CHECKLIST_HEADER_TEMPLATE", "Checklist Header Greeting", "Dear {CUSTOMER_NAME},\nGreetings from Eastern Bank PLC (EBL).\nTo process your application for {PRESET_NAME}, please provide the following required documents:"),
      Triple("CHECKLIST_REGARDS_TEMPLATE", "Checklist Footer / Regards", "For any query or assistance, please contact:\n{RM_NAME}\nRM Code: {RM_CODE}\nMobile: {RM_PHONE}\nEastern Bank PLC")
    )

    val customChecklistPresets = remember(settings) {
      settings.filter { it.settingKey.startsWith("CHECKLIST_CUSTOM_") }.map {
        Triple(it.settingKey, it.settingKey.removePrefix("CHECKLIST_CUSTOM_").replace("_", " "), it.settingValue)
      }
    }
    val checklistPresets = defaultChecklistPresets + customChecklistPresets
    var selectedPresetKey by remember { mutableStateOf(checklistPresets[0].first) }
    val currentPresetObj = checklistPresets.find { it.first == selectedPresetKey } ?: checklistPresets[0]
    val savedPresetVal = settings.find { it.settingKey == selectedPresetKey }?.settingValue ?: currentPresetObj.third
    var checklistPresetInput by remember(selectedPresetKey, savedPresetVal) { mutableStateOf(savedPresetVal) }
    var showAddNewCategoryDialog by remember { mutableStateOf(false) }
    var newCategoryKeyInput by remember { mutableStateOf("") }
    var newCategoryTitleInput by remember { mutableStateOf("") }
    var newCategoryItemsInput by remember { mutableStateOf("") }

    Card(
      modifier = Modifier.fillMaxWidth().testTag("card_checklist_sender_settings"),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Settings, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Checklist Sender & Presets Management", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = EblNavyDark)
        }
        Text("Items configured here sync automatically across all RM devices via Google Sheets.", fontSize = 11.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(10.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Select Category / Preset:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
          TextButton(
            onClick = { showAddNewCategoryDialog = true },
            modifier = Modifier.testTag("btn_add_new_checklist_category")
          ) {
            Text("+ Add Category", fontSize = 11.sp, color = EblNavyPrimary, fontWeight = FontWeight.Bold)
          }
        }
        Spacer(modifier = Modifier.height(4.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          checklistPresets.forEach { preset ->
            val isSelected = preset.first == selectedPresetKey
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = if (isSelected) EblNavyPrimary else Color(0xFFF1F5F9),
              border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
              modifier = Modifier
                .clickable { selectedPresetKey = preset.first }
                .padding(vertical = 2.dp)
            ) {
              Text(
                text = preset.second,
                color = if (isSelected) Color.White else Color.DarkGray,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
              )
            }
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = "Items for '${currentPresetObj.second}':",
          fontSize = 12.sp,
          fontWeight = FontWeight.Bold,
          color = EblNavyDark
        )
        Spacer(modifier = Modifier.height(6.dp))
        VoiceInputField(
          value = checklistPresetInput,
          onValueChange = { checklistPresetInput = it },
          label = "${currentPresetObj.second} Items",
          singleLine = false,
          maxLines = 8,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_setting_checklist_preset"
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Total ${checklistPresetInput.lines().count { it.isNotBlank() }} items",
            fontSize = 11.sp,
            color = Color.Gray
          )
          Button(
            onClick = {
              val cleanVal = checklistPresetInput.trim()
              viewModel.updateSetting(selectedPresetKey, cleanVal)
              if (selectedPresetKey == "PENDING_DOCS_LIST") {
                pendingDocsInput = cleanVal
              }
              Toast.makeText(context, "'${currentPresetObj.second}' saved & synced to all devices!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
            modifier = Modifier.testTag("btn_save_checklist_preset")
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Save & Sync")
          }
        }
      }
    }

    if (showAddNewCategoryDialog) {
      AlertDialog(
        onDismissRequest = { showAddNewCategoryDialog = false },
        title = { Text("Add New Preset Category", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = newCategoryTitleInput,
              onValueChange = { newCategoryTitleInput = it },
              label = { Text("Category Title (e.g. Special Loan Docs)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = newCategoryKeyInput,
              onValueChange = { newCategoryKeyInput = it },
              label = { Text("Setting Key") },
              placeholder = { Text("CHECKLIST_CUSTOM_...") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            VoiceInputField(
              value = newCategoryItemsInput,
              onValueChange = { newCategoryItemsInput = it },
              label = "Item List (One per line)",
              singleLine = false,
              maxLines = 6,
              modifier = Modifier.fillMaxWidth()
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val key = if (newCategoryKeyInput.isNotBlank()) {
                newCategoryKeyInput.trim().uppercase()
              } else {
                "CHECKLIST_CUSTOM_" + newCategoryTitleInput.trim().replace("\\s+".toRegex(), "_").uppercase()
              }
              if (key.isNotBlank() && newCategoryItemsInput.isNotBlank()) {
                viewModel.updateSetting(key, newCategoryItemsInput.trim())
                selectedPresetKey = key
                showAddNewCategoryDialog = false
                newCategoryKeyInput = ""
                newCategoryTitleInput = ""
                newCategoryItemsInput = ""
                Toast.makeText(context, "New category added and synced!", Toast.LENGTH_SHORT).show()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary)
          ) {
            Text("Add Category")
          }
        },
        dismissButton = {
          TextButton(onClick = { showAddNewCategoryDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
    Spacer(modifier = Modifier.height(16.dp))

    // Week Start & Timezone
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(12.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Text("Reporting Week Rule & Timezone", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        VoiceInputField(
          value = weekStartInput,
          onValueChange = { weekStartInput = it },
          label = "Week Start Day (e.g. SATURDAY)",
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
          testTag = "input_setting_week_start"
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = "Asia/Dhaka (GMT+06:00)",
          onValueChange = {},
          readOnly = true,
          label = { Text("Reporting Timezone") },
          modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
          onClick = { viewModel.updateSetting("WEEK_START_DAY", weekStartInput.trim().uppercase()) },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.align(Alignment.End)
        ) {
          Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text("Save Week Rule")
        }
      }
    }
    Spacer(modifier = Modifier.height(28.dp))
  }
}
