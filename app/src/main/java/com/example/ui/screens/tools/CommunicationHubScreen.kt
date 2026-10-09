package com.example.ui.screens.tools

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.model.CallLogEntity
import com.example.data.model.EventResponseEntity
import com.example.data.model.TeamEventEntity
import com.example.data.model.UserEntity
import com.example.ui.theme.EblGold
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.CallingService
import com.example.util.DateUtils
import com.example.util.SmsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class CustomerEntryItem(
  var name: String = "",
  var phone: String = "",
  var remarks: String = ""
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CommunicationHubScreen(
  viewModel: AppViewModel,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val currentUser by viewModel.currentUser.collectAsState()

  if (currentUser == null) return
  val user = currentUser!!
  val isMentorOrAdmin = user.role == "ADMIN" || user.role == "MENTOR"

  var selectedTab by remember { mutableIntStateOf(0) } // 0 = Team Events, 1 = Team Hub Chat, 2 = Call System & Logs

  // Dialogs
  var showCreateEventDialog by remember { mutableStateOf(false) }
  var respondingEvent by remember { mutableStateOf<TeamEventEntity?>(null) }
  var viewingResponsesEvent by remember { mutableStateOf<TeamEventEntity?>(null) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF8FAFC))
      .testTag("communication_hub_screen")
  ) {
    // Header
    Surface(
      color = EblNavyDark,
      shadowElevation = 4.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { viewModel.navigateBack() }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column {
            Text(
              text = "Team Communication Hub",
              fontSize = 16.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "Live events, customer coordination & synchronized calling",
              fontSize = 11.sp,
              color = Color.White.copy(alpha = 0.8f)
            )
          }
        }

        IconButton(
          onClick = {
            viewModel.triggerGoogleSheetsSync { _, msg ->
              Toast.makeText(context, msg ?: "Synced with Google Sheets", Toast.LENGTH_SHORT).show()
            }
          },
          modifier = Modifier.testTag("btn_hub_sync")
        ) {
          Icon(Icons.Default.Sync, contentDescription = "Sync", tint = EblGold)
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
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(15.dp))
            Text("Team Events", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
          }
        }
      )
      Tab(
        selected = selectedTab == 1,
        onClick = { selectedTab = 1 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(15.dp))
            Text("Broadcast Chat", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
          }
        }
      )
      Tab(
        selected = selectedTab == 2,
        onClick = { selectedTab = 2 },
        text = {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(15.dp))
            Text("Calls & Logs", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal)
          }
        }
      )
    }

    Box(modifier = Modifier.weight(1f)) {
      when (selectedTab) {
        0 -> TeamEventsTab(
          viewModel = viewModel,
          currentUser = user,
          isMentorOrAdmin = isMentorOrAdmin,
          onOpenCreate = { showCreateEventDialog = true },
          onOpenRespond = { respondingEvent = it },
          onViewResponses = { viewingResponsesEvent = it }
        )
        1 -> BroadcastChatTab(
          viewModel = viewModel,
          currentUser = user
        )
        2 -> CallSystemTab(
          viewModel = viewModel,
          currentUser = user
        )
      }
    }
  }

  // DIALOG 1: Create Team Event (Admin/Mentor only)
  // FIX REQUIREMENT 2 & 5: Whatever name is typed by creator IS the title - NEVER hardcode "hand delivery"!
  // Creator configures which entry fields are allowed for RMs!
  if (showCreateEventDialog) {
    var eventTitleInput by remember { mutableStateOf("") }
    var eventDescInput by remember { mutableStateOf("") }
    var eventDateInput by remember { mutableStateOf(DateUtils.formatDateOnly(DateUtils.currentDhakaMillis())) }

    // Configuration of allowed fields
    var allowCustomers by remember { mutableStateOf(true) }
    var allowFilesCount by remember { mutableStateOf(true) }
    var allowTargetDate by remember { mutableStateOf(true) }
    var allowLocation by remember { mutableStateOf(true) }
    var allowRemarks by remember { mutableStateOf(true) }

    AlertDialog(
      onDismissRequest = { showCreateEventDialog = false },
      title = {
        Text("Create Team Event / Drive", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EblNavyDark)
      },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          item {
            Text(
              text = "Give a specific custom title for this event (e.g. Card Fest, Branch Campaign, Hand Delivery Drive).",
              fontSize = 11.sp,
              color = Color.DarkGray
            )
          }
          item {
            OutlinedTextField(
              value = eventTitleInput,
              onValueChange = { eventTitleInput = it },
              label = { Text("Event Name / Title *") },
              modifier = Modifier.fillMaxWidth().testTag("input_event_title"),
              singleLine = true,
              shape = RoundedCornerShape(8.dp)
            )
          }
          item {
            OutlinedTextField(
              value = eventDateInput,
              onValueChange = { eventDateInput = it },
              label = { Text("Target Date (DD/MM/YYYY) *") },
              modifier = Modifier.fillMaxWidth().testTag("input_event_date"),
              singleLine = true,
              shape = RoundedCornerShape(8.dp)
            )
          }
          item {
            OutlinedTextField(
              value = eventDescInput,
              onValueChange = { eventDescInput = it },
              label = { Text("Event Description & RM Instructions") },
              modifier = Modifier.fillMaxWidth().testTag("input_event_desc"),
              minLines = 2,
              shape = RoundedCornerShape(8.dp)
            )
          }

          item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Configure RM Submission Fields:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
            Text("Check which inputs RMs are allowed/required to provide:", fontSize = 10.sp, color = Color.Gray)
          }

          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = allowCustomers,
                onCheckedChange = { allowCustomers = it },
                colors = CheckboxDefaults.colors(checkedColor = EblNavyPrimary)
              )
              Text("Multiple Customer Names & Phone Numbers", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
          }
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = allowFilesCount,
                onCheckedChange = { allowFilesCount = it },
                colors = CheckboxDefaults.colors(checkedColor = EblNavyPrimary)
              )
              Text("Total Files Count Number", fontSize = 11.sp)
            }
          }
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = allowLocation,
                onCheckedChange = { allowLocation = it },
                colors = CheckboxDefaults.colors(checkedColor = EblNavyPrimary)
              )
              Text("Delivery Location / Branch Address", fontSize = 11.sp)
            }
          }
          item {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Checkbox(
                checked = allowRemarks,
                onCheckedChange = { allowRemarks = it },
                colors = CheckboxDefaults.colors(checkedColor = EblNavyPrimary)
              )
              Text("Remarks & Special Notes", fontSize = 11.sp)
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (eventTitleInput.isBlank()) {
              Toast.makeText(context, "Please enter event name.", Toast.LENGTH_SHORT).show()
              return@Button
            }
            val allowedJson = JSONObject().apply {
              put("allowCustomers", allowCustomers)
              put("allowFilesCount", allowFilesCount)
              put("allowTargetDate", allowTargetDate)
              put("allowLocation", allowLocation)
              put("allowRemarks", allowRemarks)
            }.toString()

            viewModel.createTeamEvent(
              title = eventTitleInput.trim(), // EXACT name without any hardcoded prefix/suffix!
              description = eventDescInput.trim(),
              targetDate = eventDateInput.trim(),
              allowedFieldsJson = allowedJson
            )
            showCreateEventDialog = false
            Toast.makeText(context, "Event '${eventTitleInput.trim()}' created & announced to all RMs!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.testTag("btn_confirm_create_event")
        ) {
          Text("Announce Event")
        }
      },
      dismissButton = {
        TextButton(onClick = { showCreateEventDialog = false }) {
          Text("Cancel")
        }
      }
    )
  }

  // DIALOG 2: RM Submit Response to Event (with MULTIPLE CUSTOMERS: Name + Phone)
  if (respondingEvent != null) {
    val evt = respondingEvent!!
    val allowedConfig = remember(evt) {
      try { JSONObject(evt.allowedFieldsJson) } catch (_: Exception) { JSONObject() }
    }
    val canAddCustomers = allowedConfig.optBoolean("allowCustomers", true)
    val canAddFiles = allowedConfig.optBoolean("allowFilesCount", true)
    val canAddLocation = allowedConfig.optBoolean("allowLocation", true)
    val canAddRemarks = allowedConfig.optBoolean("allowRemarks", true)

    var filesCountInput by remember { mutableStateOf("1") }
    var locationInput by remember { mutableStateOf("Dhaka Principal Branch") }
    var remarksInput by remember { mutableStateOf("") }

    val customerEntries = remember {
      mutableStateListOf(CustomerEntryItem(name = "", phone = "", remarks = ""))
    }

    AlertDialog(
      onDismissRequest = { respondingEvent = null },
      title = {
        Column {
          Text("Submit Details for:", fontSize = 11.sp, color = Color.Gray)
          Text(evt.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = EblNavyDark)
          Text("Target Date: ${evt.targetDate}", fontSize = 11.sp, color = EblNavyPrimary)
        }
      },
      text = {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          if (canAddFiles) {
            item {
              OutlinedTextField(
                value = filesCountInput,
                onValueChange = { filesCountInput = it },
                label = { Text("Total Files Count") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("input_event_files_count"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
              )
            }
          }

          if (canAddLocation) {
            item {
              OutlinedTextField(
                value = locationInput,
                onValueChange = { locationInput = it },
                label = { Text("Delivery / Branch Location") },
                modifier = Modifier.fillMaxWidth().testTag("input_event_location"),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
              )
            }
          }

          // MULTIPLE CUSTOMER SECTION (Requirement 5!)
          if (canAddCustomers) {
            item {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Customer Details (${customerEntries.size} customer(s))",
                  fontSize = 13.sp,
                  fontWeight = FontWeight.Bold,
                  color = EblNavyDark
                )
                Button(
                  onClick = {
                    customerEntries.add(CustomerEntryItem(name = "", phone = ""))
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                  modifier = Modifier.testTag("btn_add_customer_entry")
                ) {
                  Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(2.dp))
                  Text("Add More", fontSize = 11.sp)
                }
              }
            }

            items(customerEntries.size) { idx ->
              val item = customerEntries[idx]
              Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Column(modifier = Modifier.padding(8.dp)) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text("Customer #${idx + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EblNavyPrimary)
                    if (customerEntries.size > 1) {
                      IconButton(
                        onClick = { customerEntries.removeAt(idx) },
                        modifier = Modifier.size(20.dp)
                      ) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red, modifier = Modifier.size(14.dp))
                      }
                    }
                  }
                  Spacer(modifier = Modifier.height(4.dp))
                  OutlinedTextField(
                    value = item.name,
                    onValueChange = {
                      customerEntries[idx] = item.copy(name = it)
                    },
                    label = { Text("Customer Name *") },
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_entry_name_$idx")
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  OutlinedTextField(
                    value = item.phone,
                    onValueChange = {
                      customerEntries[idx] = item.copy(phone = it)
                    },
                    label = { Text("Customer Mobile Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("input_entry_phone_$idx")
                  )
                }
              }
            }
          }

          if (canAddRemarks) {
            item {
              OutlinedTextField(
                value = remarksInput,
                onValueChange = { remarksInput = it },
                label = { Text("Remarks") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val customersArray = JSONArray()
            for (c in customerEntries) {
              if (c.name.isNotBlank() || c.phone.isNotBlank()) {
                customersArray.put(JSONObject().apply {
                  put("name", c.name.trim())
                  put("phone", c.phone.trim())
                })
              }
            }

            viewModel.submitEventResponse(
              eventId = evt.eventId,
              filesCount = filesCountInput.toIntOrNull() ?: 1,
              requestedDate = evt.targetDate,
              location = locationInput.trim(),
              remarks = remarksInput.trim(),
              customersJson = customersArray.toString()
            )
            respondingEvent = null
            Toast.makeText(context, "Event submission recorded with ${customersArray.length()} customer(s)!", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
          modifier = Modifier.testTag("btn_submit_event_response")
        ) {
          Text("Submit Response")
        }
      },
      dismissButton = {
        TextButton(onClick = { respondingEvent = null }) {
          Text("Cancel")
        }
      }
    )
  }

  // DIALOG 3: View RM Responses for an Event (Admin/Mentor)
  if (viewingResponsesEvent != null) {
    val evt = viewingResponsesEvent!!
    val responses: List<EventResponseEntity> by viewModel.getResponsesForEventFlow(evt.eventId).collectAsState(initial = emptyList())

    AlertDialog(
      onDismissRequest = { viewingResponsesEvent = null },
      title = {
        Column {
          Text("Submitted RM Responses", fontSize = 11.sp, color = Color.Gray)
          Text(evt.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Text("Total RM Submissions: ${responses.size}", fontSize = 11.sp, color = EblNavyPrimary)
        }
      },
      text = {
        if (responses.isEmpty()) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No RMs have submitted details for this event yet.", fontSize = 12.sp, color = Color.Gray)
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            items(responses) { resp ->
              EventResponseItemCard(
                response = resp,
                onCallNumber = { phone, name ->
                  CallingService.dialPhoneNumber(context, phone)
                  viewModel.logCallRecord(
                    recipientRmCode = resp.rmCode,
                    recipientName = name,
                    recipientMobile = phone,
                    durationSeconds = 60
                  )
                },
                onSmsNumber = { phone, name ->
                  SmsService.launchSmsApp(context, phone, "Greetings from EBL regarding ${evt.title}.")
                }
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { viewingResponsesEvent = null }) {
          Text("Close")
        }
      }
    )
  }
}

@Composable
private fun TeamEventsTab(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  isMentorOrAdmin: Boolean,
  onOpenCreate: () -> Unit,
  onOpenRespond: (TeamEventEntity) -> Unit,
  onViewResponses: (TeamEventEntity) -> Unit
) {
  val events by viewModel.teamEvents.collectAsState()

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text("Active Team Events & Campaigns", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Text("Participate & coordinate customer deliveries directly", fontSize = 11.sp, color = Color.Gray)
        }
        if (isMentorOrAdmin) {
          Button(
            onClick = onOpenCreate,
            colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier.testTag("btn_create_event")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("New Event", fontSize = 12.sp)
          }
        }
      }
    }

    if (events.isEmpty()) {
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
              Icon(Icons.Default.Event, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(48.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("No active events announced yet.", fontSize = 13.sp, color = Color.Gray)
              if (isMentorOrAdmin) {
                Spacer(modifier = Modifier.height(4.dp))
                Text("Tap '+ New Event' to start an event drive for all RMs.", fontSize = 11.sp, color = EblNavyPrimary)
              }
            }
          }
        }
      }
    } else {
      items(events) { evt ->
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth().testTag("event_card_${evt.eventId}")
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              // EXACT event title - NO hardcoded strings!
              Text(
                text = evt.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = EblNavyDark,
                modifier = Modifier.weight(1f)
              )
              Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (evt.status == "ACTIVE") Color(0xFFDCFCE7) else Color(0xFFF1F5F9)
              ) {
                Text(
                  text = evt.status,
                  color = if (evt.status == "ACTIVE") Color(0xFF166534) else Color.Gray,
                  fontSize = 10.sp,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
              }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Icon(Icons.Default.DateRange, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(13.dp))
              Text("Target Date: ${evt.targetDate}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = EblNavyPrimary)
              Text("•", fontSize = 11.sp, color = Color.Gray)
              Text("By: ${evt.creatorName} (${evt.creatorRmCode})", fontSize = 11.sp, color = Color.Gray)
            }

            if (evt.description.isNotBlank()) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(evt.description, fontSize = 12.sp, color = Color.DarkGray, lineHeight = 16.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              if (isMentorOrAdmin) {
                OutlinedButton(
                  onClick = { onViewResponses(evt) },
                  shape = RoundedCornerShape(6.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.testTag("btn_view_responses_${evt.eventId}")
                ) {
                  Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(14.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("View RM Submissions", fontSize = 11.sp)
                }
              } else {
                Spacer(modifier = Modifier.width(1.dp))
              }

              Button(
                onClick = { onOpenRespond(evt) },
                colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
                shape = RoundedCornerShape(6.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag("btn_respond_event_${evt.eventId}")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Submit Details", fontSize = 11.sp)
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

@Composable
private fun EventResponseItemCard(
  response: EventResponseEntity,
  onCallNumber: (String, String) -> Unit,
  onSmsNumber: (String, String) -> Unit
) {
  var isExpanded by remember { mutableStateOf(false) }

  val customersList = remember(response.customersJson) {
    val list = mutableListOf<CustomerEntryItem>()
    try {
      val arr = JSONArray(response.customersJson)
      for (i in 0 until arr.length()) {
        val o = arr.getJSONObject(i)
        list.add(CustomerEntryItem(o.optString("name"), o.optString("phone")))
      }
    } catch (_: Exception) {}
    list
  }

  Card(
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(response.rmName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Text("RM Code: ${response.rmCode} | Files: ${response.filesCount}", fontSize = 11.sp, color = Color.DarkGray)
          if (response.location.isNotBlank()) {
            Text("Branch: ${response.location}", fontSize = 10.sp, color = Color.Gray)
          }
        }
        IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(24.dp)) {
          Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
        }
      }

      if (response.remarks.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text("Note: ${response.remarks}", fontSize = 10.sp, color = Color.DarkGray)
      }

      // MULTIPLE CUSTOMER DISPLAY WITH CALL/SMS BUTTONS (Requirement 5!)
      if (customersList.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
          "Customer Details (${customersList.size}):",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = EblNavyPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))

        customersList.forEachIndexed { i, cust ->
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
          ) {
            Row(
              modifier = Modifier.padding(6.dp).fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text("${i + 1}. ${cust.name}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
                Text(cust.phone, fontSize = 10.sp, color = Color.DarkGray)
              }
              if (cust.phone.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                  IconButton(
                    onClick = { onCallNumber(cust.phone, cust.name) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Call, contentDescription = "Call", tint = Color(0xFF16A34A), modifier = Modifier.size(14.dp))
                  }
                  IconButton(
                    onClick = { onSmsNumber(cust.phone, cust.name) },
                    modifier = Modifier.size(24.dp)
                  ) {
                    Icon(Icons.Default.Message, contentDescription = "SMS", tint = Color(0xFF2563EB), modifier = Modifier.size(14.dp))
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BroadcastChatTab(
  viewModel: AppViewModel,
  currentUser: UserEntity
) {
  val messages by viewModel.teamHubMessages.collectAsState()
  var inputMessage by remember { mutableStateOf("") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(12.dp)
  ) {
    LazyColumn(
      modifier = Modifier
        .weight(1f)
        .fillMaxWidth(),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      if (messages.isEmpty()) {
        item {
          Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("No announcements or messages yet. Post a broadcast below.", fontSize = 12.sp, color = Color.Gray)
          }
        }
      } else {
        items(messages) { msg ->
          val isMe = msg.senderRmCode.equals(currentUser.rmCode, ignoreCase = true)
          val bubbleColor = if (isMe) EblNavyPrimary else Color.White
          val textColor = if (isMe) Color.White else Color.Black

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
          ) {
            Card(
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(containerColor = bubbleColor),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
              modifier = Modifier.fillMaxWidth(0.85f)
            ) {
              Column(modifier = Modifier.padding(10.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  Text(
                    text = "${msg.senderName} (${msg.senderRole})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isMe) EblGold else EblNavyPrimary
                  )
                  Text(
                    text = DateUtils.formatTimeOnly(msg.timestamp),
                    fontSize = 9.sp,
                    color = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray
                  )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = msg.messageText,
                  fontSize = 12.sp,
                  lineHeight = 17.sp,
                  color = textColor
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Send bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically
    ) {
      OutlinedTextField(
        value = inputMessage,
        onValueChange = { inputMessage = it },
        placeholder = { Text("Write team broadcast or announcement...", fontSize = 12.sp) },
        modifier = Modifier.weight(1f).testTag("input_team_chat_message"),
        shape = RoundedCornerShape(24.dp),
        singleLine = true
      )
      Spacer(modifier = Modifier.width(6.dp))
      IconButton(
        onClick = {
          if (inputMessage.isNotBlank()) {
            viewModel.sendChatMessage(inputMessage.trim(), null)
            inputMessage = ""
          }
        },
        modifier = Modifier
          .size(44.dp)
          .background(EblNavyPrimary, CircleShape)
          .testTag("btn_send_team_message")
      ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
      }
    }
  }
}

@Composable
private fun CallSystemTab(
  viewModel: AppViewModel,
  currentUser: UserEntity
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val allCallLogs by viewModel.callLogs.collectAsState()

  var dialNumberInput by remember { mutableStateOf("") }
  var recipientNameInput by remember { mutableStateOf("") }
  var isCallActive by remember { mutableStateOf(false) }
  var callSeconds by remember { mutableIntStateOf(0) }

  // Call timer loop
  LaunchedEffect(isCallActive) {
    if (isCallActive) {
      callSeconds = 0
      while (isCallActive) {
        delay(1000)
        callSeconds++
      }
    }
  }

  LazyColumn(
    modifier = Modifier
      .fillMaxSize()
      .padding(14.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)
  ) {
    // Quick Dialer Card
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().testTag("card_call_dialer")
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Text("Quick Dial & Call Assistant", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          Text("Direct mobile dialing + voice connection tones with auto-synced sheet logging", fontSize = 11.sp, color = Color.Gray)
          Spacer(modifier = Modifier.height(10.dp))

          OutlinedTextField(
            value = dialNumberInput,
            onValueChange = { dialNumberInput = it },
            label = { Text("Phone Number to Call") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().testTag("input_call_number"),
            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = EblNavyPrimary) }
          )
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = recipientNameInput,
            onValueChange = { recipientNameInput = it },
            label = { Text("Recipient / Customer Name (Optional)") },
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = EblNavyPrimary) }
          )

          Spacer(modifier = Modifier.height(12.dp))

          if (isCallActive) {
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFDCFCE7),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                  Column {
                    Text("Call Connected", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    Text("Duration: %02d:%02d".format(callSeconds / 60, callSeconds % 60), fontSize = 11.sp, color = Color(0xFF15803D))
                  }
                }
                Button(
                  onClick = {
                    isCallActive = false
                    CallingService.playEndCallTone()
                    val targetName = recipientNameInput.ifBlank { "Dialed Recipient" }
                    val targetNum = dialNumberInput.trim()
                    viewModel.logCallRecord(
                      recipientRmCode = "DIRECT_CALL",
                      recipientName = targetName,
                      recipientMobile = targetNum,
                      durationSeconds = callSeconds
                    )
                    Toast.makeText(context, "Call logged and synced to Google Sheets!", Toast.LENGTH_SHORT).show()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("End Call")
                }
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              // Direct Phone Dialer Launch
              Button(
                onClick = {
                  if (dialNumberInput.isBlank()) {
                    Toast.makeText(context, "Please enter a phone number.", Toast.LENGTH_SHORT).show()
                    return@Button
                  }
                  val num = dialNumberInput.trim()
                  val name = recipientNameInput.ifBlank { "Customer" }
                  CallingService.dialPhoneNumber(context, num)
                  viewModel.logCallRecord(
                    recipientRmCode = "EXTERNAL_DIAL",
                    recipientName = name,
                    recipientMobile = num,
                    durationSeconds = 45
                  )
                  Toast.makeText(context, "Launching phone dialer...", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("btn_dial_phone")
              ) {
                Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Direct Dial", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }

              // In-app voice tone test & simulator
              Button(
                onClick = {
                  if (dialNumberInput.isBlank()) {
                    Toast.makeText(context, "Please enter a phone number.", Toast.LENGTH_SHORT).show()
                    return@Button
                  }
                  CallingService.playDialTone()
                  isCallActive = true
                  coroutineScope.launch {
                    delay(3000)
                    CallingService.playConnectedTone()
                  }
                },
                colors = ButtonDefaults.buttonColors(containerColor = EblNavyPrimary),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f).testTag("btn_start_voice_call")
              ) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Voice Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // Call Logs Table
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Synchronized Call Records (${allCallLogs.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
        Surface(
          shape = RoundedCornerShape(4.dp),
          color = Color(0xFFDCFCE7)
        ) {
          Text("Auto-synced to Sheet: Call_Logs", fontSize = 9.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
      }
    }

    if (allCallLogs.isEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          modifier = Modifier.fillMaxWidth()
        ) {
          Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Text("No calls recorded yet. All calls will be automatically preserved in Google Sheets.", fontSize = 11.sp, color = Color.Gray)
          }
        }
      }
    } else {
      items(allCallLogs) { call ->
        Card(
          shape = RoundedCornerShape(8.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth().testTag("call_log_item_${call.callId}")
        ) {
          Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(shape = CircleShape, color = Color(0xFFDCFCE7), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                  Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                }
              }
              Column {
                Text(call.recipientName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
                Text("${call.recipientMobile} • Caller: ${call.callerName}", fontSize = 10.sp, color = Color.Gray)
              }
            }
            Column(horizontalAlignment = Alignment.End) {
              Text("${call.durationSeconds}s", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
              Text(DateUtils.formatDateTime(call.timestamp), fontSize = 9.sp, color = Color.Gray)
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
