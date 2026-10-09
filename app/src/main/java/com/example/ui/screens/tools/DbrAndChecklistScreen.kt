package com.example.ui.screens.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.UserEntity
import com.example.ui.theme.EblGold
import com.example.ui.theme.EblGreen
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.SmsService
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DbrAndChecklistScreen(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  onNavigateBack: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var selectedTab by remember { mutableStateOf(0) } // 0 = Checklist Sender, 1 = DBR Calculator
  val appSettings by viewModel.appSettings.collectAsState()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF8FAFC))
      .testTag("dbr_checklist_screen")
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
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onNavigateBack,
          modifier = Modifier.testTag("btn_back_dbr_checklist")
        ) {
          Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = Color.White
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "EBL Tools: DBR & Checklist Sender",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
          )
          Text(
            text = "Auto-synced presets & verified debt burden calculations",
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.8f)
          )
        }
      }
    }

    SecondaryTabRow(
      selectedTabIndex = selectedTab,
      containerColor = Color.White
    ) {
      Tab(
        selected = selectedTab == 0,
        onClick = { selectedTab = 0 },
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("Checklist Sender", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
          }
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("DBR Calculator", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
          }
        }
      )
    }

    if (selectedTab == 0) {
      ChecklistSenderSection(
        viewModel = viewModel,
        currentUser = currentUser,
        appSettings = appSettings
      )
    } else {
      DbrCalculatorSection()
    }
  }
}

@Composable
private fun ChecklistSenderSection(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  appSettings: List<com.example.data.model.AppSettingEntity>
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  // Default Presets + Custom Presets dynamically loaded from Universal Settings (Fix Requirement 6!)
  val defaultPresets = listOf(
    Triple(
      "CHECKLIST_SALARIED_DOCS",
      "Salaried Credit Card",
      "NID / Smart Card / Valid Passport photocopy\n2 copies recent Passport size photographs\nLatest E-TIN Certificate & Tax Return Acknowledgment Slip\nLatest Salary Certificate / Original Pay Slips (last 3 months)\n6-month Salary Account Statement (with bank seal & signature)\nOffice ID Card photocopy & Visiting Card\nUtility Bill photocopy (Electricity / WASA / Gas residence)"
    ),
    Triple(
      "CHECKLIST_BUSINESS_DOCS",
      "Business Person Credit Card",
      "National ID Card (NID) photocopy\n2 copies recent Passport size photographs\nValid Trade License (last 3-5 years renewal copies)\n12-month Business & Personal Bank Account Statement (sealed)\nLatest E-TIN Certificate & Tax Return Acknowledgment Slip\nVisiting card & Memorandum of Association / Partnership Deed\nUtility bill of residence & business premises"
    ),
    Triple(
      "CHECKLIST_LOAN_DOCS",
      "Loan Application Docs",
      "National ID Card (NID) photocopy\n2 copies recent Passport size photographs\nLatest E-TIN Certificate & Tax Return Assessment Slip\nIncome proof (Salary Certificate / 6-12 month Bank Statement)\nOffice ID / Trade License photocopy\nUtility Bill photocopy (residence)"
    )
  )

  // Dynamically load any custom preset created by Admin
  val customPresets = remember(appSettings) {
    appSettings.filter { it.settingKey.startsWith("CHECKLIST_CUSTOM_") }.map {
      val label = it.settingKey.removePrefix("CHECKLIST_CUSTOM_").replace("_", " ")
        .lowercase().replaceFirstChar { c -> c.uppercase() }
      Triple(it.settingKey, label, it.settingValue)
    }
  }

  val allPresets = defaultPresets + customPresets
  var selectedPresetKey by remember { mutableStateOf(allPresets[0].first) }

  // Get the current template string from appSettings if available, or default
  val currentPresetContent = remember(selectedPresetKey, appSettings) {
    val fromSettings = appSettings.find { it.settingKey == selectedPresetKey }?.settingValue
    if (!fromSettings.isNullOrBlank()) fromSettings else (allPresets.find { it.first == selectedPresetKey }?.third ?: "")
  }

  val headerTemplate = remember(appSettings) {
    appSettings.find { it.settingKey == "CHECKLIST_HEADER_TEMPLATE" }?.settingValue
      ?: "Dear {CUSTOMER_NAME},\nGreetings from Eastern Bank PLC (EBL).\nTo process your application for {PRESET_NAME}, please provide the following required documents:"
  }

  val regardsTemplate = remember(appSettings) {
    appSettings.find { it.settingKey == "CHECKLIST_REGARDS_TEMPLATE" }?.settingValue
      ?: "For any query or assistance, please contact:\n{RM_NAME}\nRM Code: {RM_CODE}\nMobile: {RM_PHONE}\nEastern Bank PLC"
  }

  // Parse lines into selectable checklist items
  val lines = remember(currentPresetContent) {
    currentPresetContent.lines().map { it.trim() }.filter { it.isNotBlank() }
  }

  val checkedStates = remember(currentPresetContent) {
    mutableStateMapOf<String, Boolean>().apply {
      lines.forEach { put(it, true) }
    }
  }

  var customerNameInput by remember { mutableStateOf("") }
  var customerMobileInput by remember { mutableStateOf("") }

  // Build formatted message
  val selectedPresetName = allPresets.find { it.first == selectedPresetKey }?.second ?: "Application"
  val activeHeader = headerTemplate
    .replace("{CUSTOMER_NAME}", customerNameInput.ifBlank { "Valued Customer" })
    .replace("{PRESET_NAME}", selectedPresetName)

  val selectedItems = lines.filter { checkedStates[it] == true }
  val itemsText = selectedItems.mapIndexed { idx, item -> "${idx + 1}. $item" }.joinToString("\n")

  val activeRegards = regardsTemplate
    .replace("{RM_NAME}", currentUser.name)
    .replace("{RM_CODE}", currentUser.rmCode)
    .replace("{RM_PHONE}", currentUser.mobile.ifBlank { "017XXXXXXXX" })

  val fullChecklistMessage = "$activeHeader\n\n$itemsText\n\n$activeRegards"

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Preset Selector
    item {
      Text("Select Document Preset Category:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
      Spacer(modifier = Modifier.height(6.dp))
      LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
      ) {
        items(allPresets) { preset ->
          val isSelected = preset.first == selectedPresetKey
          FilterChip(
            selected = isSelected,
            onClick = { selectedPresetKey = preset.first },
            label = {
              Text(
                text = preset.second,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
              )
            },
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = EblNavyPrimary,
              selectedLabelColor = Color.White
            ),
            modifier = Modifier.testTag("preset_chip_${preset.first}")
          )
        }
      }
    }

    // Customer Info Card
    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Recipient Customer Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = customerNameInput,
              onValueChange = { customerNameInput = it },
              label = { Text("Customer Name") },
              modifier = Modifier.weight(1f).testTag("input_checklist_customer_name"),
              shape = RoundedCornerShape(8.dp),
              singleLine = true,
              leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(18.dp)) }
            )
            OutlinedTextField(
              value = customerMobileInput,
              onValueChange = { customerMobileInput = it },
              label = { Text("Mobile Number") },
              modifier = Modifier.weight(1f).testTag("input_checklist_customer_mobile"),
              shape = RoundedCornerShape(8.dp),
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
              leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(18.dp)) }
            )
          }
        }
      }
    }

    // Document Items Checklist
    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("Required Document Items (${selectedItems.size}/${lines.size} selected)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
            Row {
              Text(
                text = "Select All",
                fontSize = 11.sp,
                color = EblNavyPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                  .padding(4.dp)
                  .testTag("btn_select_all_items")
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          HorizontalDivider(color = Color(0xFFE2E8F0))

          lines.forEach { line ->
            val isChecked = checkedStates[line] ?: true
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = isChecked,
                onCheckedChange = { checkedStates[line] = it },
                colors = CheckboxDefaults.colors(checkedColor = EblNavyPrimary)
              )
              Text(
                text = line,
                fontSize = 12.sp,
                color = if (isChecked) Color.Black else Color.Gray,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }
    }

    // Message Preview & Send Actions
    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Checklist Message Preview", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Spacer(modifier = Modifier.height(6.dp))
          Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = fullChecklistMessage,
              fontSize = 11.sp,
              lineHeight = 16.sp,
              color = Color.DarkGray,
              modifier = Modifier.padding(10.dp)
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // Action Buttons
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = {
                if (customerMobileInput.isBlank()) {
                  Toast.makeText(context, "Please enter customer mobile number.", Toast.LENGTH_SHORT).show()
                  return@Button
                }
                coroutineScope.launch {
                  val success = SmsService.sendSms(context, customerMobileInput.trim(), fullChecklistMessage)
                  if (success) {
                    Toast.makeText(context, "Checklist SMS sent to ${customerMobileInput.trim()}!", Toast.LENGTH_SHORT).show()
                  } else {
                    SmsService.launchSmsApp(context, customerMobileInput.trim(), fullChecklistMessage)
                  }
                  // Log in Room SMS Notification and sync to Google Sheets
                  viewModel.sendSmsNotification(
                    recipientRmCode = currentUser.rmCode,
                    recipientName = customerNameInput.ifBlank { "Valued Customer" },
                    recipientMobile = customerMobileInput.trim(),
                    actionType = "CHECKLIST",
                    targetType = "CHECKLIST",
                    fileId = null,
                    customerName = customerNameInput.ifBlank { "Valued Customer" },
                    messageText = fullChecklistMessage
                  )
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).testTag("btn_send_checklist_sms")
            ) {
              Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Send SMS", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            Button(
              onClick = {
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_TEXT, fullChecklistMessage)
                  type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Document Checklist"))
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).testTag("btn_share_checklist")
            ) {
              Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Share", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
              onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("EBL Checklist", fullChecklistMessage)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Checklist copied to clipboard!", Toast.LENGTH_SHORT).show()
              },
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(0.9f).testTag("btn_copy_checklist")
            ) {
              Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Copy", fontSize = 11.sp)
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
private fun DbrCalculatorSection() {
  val context = LocalContext.current

  var grossSalaryInput by remember { mutableStateOf("") }
  var otherIncomeInput by remember { mutableStateOf("") }
  var existingLoanEmiInput by remember { mutableStateOf("") }
  var existingCardLimitInput by remember { mutableStateOf("") }
  var proposedEmiInput by remember { mutableStateOf("") }

  val gross = grossSalaryInput.toDoubleOrNull() ?: 0.0
  val other = otherIncomeInput.toDoubleOrNull() ?: 0.0
  val totalIncome = gross + other

  val existingLoanEmi = existingLoanEmiInput.toDoubleOrNull() ?: 0.0
  val existingCardLimit = existingCardLimitInput.toDoubleOrNull() ?: 0.0
  // Bangladesh Bank standard credit card minimum liability estimate = 5% of total card limit
  val cardMonthlyLiability = existingCardLimit * 0.05
  val proposedEmi = proposedEmiInput.toDoubleOrNull() ?: 0.0

  val totalObligations = existingLoanEmi + cardMonthlyLiability + proposedEmi
  val dbrPercentage = if (totalIncome > 0) (totalObligations / totalIncome) * 100 else 0.0
  val netDisposableIncome = (totalIncome - totalObligations).coerceAtLeast(0.0)

  // Max BB threshold: 50% for standard personal retail loans/cards, 30-40% conservative
  val isEligible = dbrPercentage in 0.01..50.00
  val isExceeded = dbrPercentage > 50.00

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_dbr_inputs")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Monthly Income (BDT)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = grossSalaryInput,
            onValueChange = { grossSalaryInput = it },
            label = { Text("Monthly Gross Salary / Business Income") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_dbr_salary")
          )
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = otherIncomeInput,
            onValueChange = { otherIncomeInput = it },
            label = { Text("Other Verified Income (House rent, consulting, etc.)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }

    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Existing & Proposed Liabilities (BDT)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = existingLoanEmiInput,
            onValueChange = { existingLoanEmiInput = it },
            label = { Text("Existing Loan Monthly EMI (Personal / Auto / Home)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_dbr_existing_emi")
          )
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = existingCardLimitInput,
            onValueChange = { existingCardLimitInput = it },
            label = { Text("Total Existing Credit Card Limit (5% taken as liability)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_dbr_card_limit")
          )
          Spacer(modifier = Modifier.height(8.dp))

          OutlinedTextField(
            value = proposedEmiInput,
            onValueChange = { proposedEmiInput = it },
            label = { Text("Proposed EBL Card / Loan Monthly EMI") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_dbr_proposed_emi")
          )
        }
      }
    }

    // Results Card
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isExceeded) Color(0xFFFEF2F2) else if (isEligible) Color(0xFFF0FDF4) else Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (isExceeded) Color(0xFFFECACA) else if (isEligible) Color(0xFFBBF7D0) else Color(0xFFCBD5E1)),
        modifier = Modifier.fillMaxWidth().testTag("card_dbr_results")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text("Debt Burden Ratio (DBR)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
              Text("Bangladesh Bank Standard Ceiling: 50.00%", fontSize = 11.sp, color = Color.Gray)
            }
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = if (isExceeded) Color(0xFFDC2626) else if (isEligible) Color(0xFF16A34A) else Color.Gray
            ) {
              Text(
                text = "%.2f%%".format(dbrPercentage),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(12.dp))
          HorizontalDivider(color = Color.LightGray.copy(alpha = 0.5f))
          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Total Monthly Income:", fontSize = 12.sp, color = Color.DarkGray)
            Text("BDT %.2f".format(totalIncome), fontSize = 12.sp, fontWeight = FontWeight.Bold)
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Total Monthly Obligations:", fontSize = 12.sp, color = Color.DarkGray)
            Text("BDT %.2f".format(totalObligations), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
          }
          Spacer(modifier = Modifier.height(4.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Text("Net Disposable Income:", fontSize = 12.sp, color = Color.DarkGray)
            Text("BDT %.2f".format(netDisposableIncome), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
          }

          Spacer(modifier = Modifier.height(12.dp))

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isExceeded) Color(0xFFFEE2E2) else if (isEligible) Color(0xFFDCFCE7) else Color(0xFFF1F5F9),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                if (isExceeded) Icons.Default.Error else if (isEligible) Icons.Default.CheckCircle else Icons.Default.Calculate,
                contentDescription = null,
                tint = if (isExceeded) Color(0xFFDC2626) else if (isEligible) Color(0xFF16A34A) else Color.Gray,
                modifier = Modifier.size(20.dp)
              )
              Text(
                text = if (isExceeded) "Exceeds 50% Ceiling! Customer is not eligible under standard retail policy."
                       else if (isEligible) "Eligible! DBR is within acceptable banking limits (<= 50%)."
                       else "Please enter monthly income & liabilities above to compute DBR.",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isExceeded) Color(0xFF991B1B) else if (isEligible) Color(0xFF166534) else Color.DarkGray
              )
            }
          }
        }
      }
    }

    item {
      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}
