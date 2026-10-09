package com.example.ui.screens.reports

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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.example.data.model.UserEntity
import com.example.ui.theme.EblGold
import com.example.ui.theme.EblGreen
import com.example.ui.theme.EblNavyDark
import com.example.ui.theme.EblNavyPrimary
import com.example.ui.viewmodel.AppViewModel
import com.example.util.DateUtils
import com.example.util.ExcelReportGenerator
import com.example.util.PdfReportGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ReportsScreen(
  viewModel: AppViewModel,
  currentUser: UserEntity,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val stats by viewModel.kpiStats.collectAsState()
  val allFiles by viewModel.filteredFiles.collectAsState()
  val rmPerformanceList by viewModel.rmPerformanceList.collectAsState()
  val timeFilter by viewModel.selectedTimeFilter.collectAsState()

  var isGeneratingPdf by remember { mutableStateOf(false) }
  var isGeneratingExcel by remember { mutableStateOf(false) }

  val isMentorOrAdmin = currentUser.role == "ADMIN" || currentUser.role == "MENTOR"

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(Color(0xFFF8FAFC))
      .padding(16.dp)
      .testTag("reports_screen"),
    verticalArrangement = Arrangement.spacedBy(16.dp)
  ) {
    // Header & Export Buttons
    item {
      Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = EblNavyDark),
        modifier = Modifier.fillMaxWidth().testTag("card_reports_header")
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "Operational & Performance Analytics",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Text(
                text = "Export verified banking reports in PDF & Excel format",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.8f)
              )
            }
            Icon(
              Icons.Default.Assessment,
              contentDescription = null,
              tint = EblGold,
              modifier = Modifier.size(32.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))
          HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
          Spacer(modifier = Modifier.height(14.dp))

          // Export actions
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = {
                if (allFiles.isEmpty()) {
                  Toast.makeText(context, "No file records found for selected period.", Toast.LENGTH_SHORT).show()
                  return@Button
                }
                isGeneratingPdf = true
                coroutineScope.launch {
                  val success: Boolean = withContext(Dispatchers.IO) {
                    PdfReportGenerator.generateAndSharePdfReport(
                      context = context,
                      files = allFiles,
                      currentUser = currentUser,
                      timeFilterName = timeFilter.name.replace("_", " "),
                      generatedBy = "${currentUser.name} (${currentUser.rmCode})"
                    )
                  }
                  isGeneratingPdf = false
                  if (!success) {
                    Toast.makeText(context, "Failed to create PDF report.", Toast.LENGTH_SHORT).show()
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = EblGold),
              modifier = Modifier.weight(1f).testTag("btn_export_pdf"),
              shape = RoundedCornerShape(8.dp),
              enabled = !isGeneratingPdf
            ) {
              if (isGeneratingPdf) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = EblNavyDark, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generating PDF...", color = EblNavyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Default.Description, contentDescription = null, tint = EblNavyDark, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export PDF", color = EblNavyDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }

            Button(
              onClick = {
                if (allFiles.isEmpty()) {
                  Toast.makeText(context, "No file records found for selected period.", Toast.LENGTH_SHORT).show()
                  return@Button
                }
                isGeneratingExcel = true
                coroutineScope.launch {
                  val success: Boolean = withContext(Dispatchers.IO) {
                    ExcelReportGenerator.generateAndShareExcelReport(
                      context = context,
                      files = allFiles,
                      currentUser = currentUser,
                      timeFilterName = timeFilter.name.replace("_", " "),
                      generatedBy = "${currentUser.name} (${currentUser.rmCode})"
                    )
                  }
                  isGeneratingExcel = false
                  if (!success) {
                    Toast.makeText(context, "Failed to create Excel report.", Toast.LENGTH_SHORT).show()
                  }
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
              modifier = Modifier.weight(1f).testTag("btn_export_excel"),
              shape = RoundedCornerShape(8.dp),
              enabled = !isGeneratingExcel
            ) {
              if (isGeneratingExcel) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generating Excel...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              } else {
                Icon(Icons.Default.TableChart, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Export Excel", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }
    }

    // Time Filter Chips
    item {
      Column {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Icon(Icons.Default.FilterList, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(18.dp))
          Text("Time Filter:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
          items(DateUtils.TimeFilter.values()) { tf ->
            val isSelected = tf == timeFilter
            FilterChip(
              selected = isSelected,
              onClick = { viewModel.selectedTimeFilter.value = tf },
              label = {
                Text(
                  text = tf.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() },
                  fontSize = 11.sp,
                  fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                )
              },
              colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = EblNavyPrimary,
                selectedLabelColor = Color.White
              ),
              modifier = Modifier.testTag("filter_chip_${tf.name}")
            )
          }
        }
      }
    }

    // Performance Summary Cards
    item {
      Text("Operational Performance Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SummaryKpiBox("Total Files", stats.totalFiles.toString(), EblNavyPrimary, Modifier.weight(1f))
        SummaryKpiBox("STC / Production", stats.stc.toString(), Color(0xFF16A34A), Modifier.weight(1f))
        SummaryKpiBox("Submitted", stats.submitted.toString(), Color(0xFF2563EB), Modifier.weight(1f))
        SummaryKpiBox("Approved", stats.approved.toString(), Color(0xFF0F766E), Modifier.weight(1f))
      }
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SummaryKpiBox("Analyst Recv", stats.analystReceive.toString(), Color(0xFFD97706), Modifier.weight(1f))
        SummaryKpiBox("Query", stats.query.toString(), Color(0xFFDC2626), Modifier.weight(1f))
        SummaryKpiBox("RTS", stats.returnToSource.toString(), Color(0xFF9333EA), Modifier.weight(1f))
        SummaryKpiBox("Declined", stats.declined.toString(), Color(0xFF6B7280), Modifier.weight(1f))
      }
    }

    // Product Breakdown
    item {
      Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Default.PieChart, contentDescription = null, tint = EblNavyPrimary, modifier = Modifier.size(18.dp))
            Text("Product Category Volume", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
          }
          Spacer(modifier = Modifier.height(10.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            ProductStatPill("Credit Card", stats.creditCardCount, stats.stcCreditCardCount, Color(0xFF2563EB))
            ProductStatPill("Corporate Card", stats.corporateCardCount, stats.stcCorporateCardCount, Color(0xFF9333EA))
            ProductStatPill("B2B", stats.b2bCount, stats.stcB2bCount, Color(0xFF0D9488))
          }
        }
      }
    }

    // RM Performance Ranking Table (if Admin/Mentor or RM looking at their ranking)
    if (isMentorOrAdmin && rmPerformanceList.isNotEmpty()) {
      item {
        Card(
          shape = RoundedCornerShape(10.dp),
          colors = CardDefaults.cardColors(containerColor = Color.White),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth().testTag("card_rm_rankings")
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text("RM Achievement & Production Rankings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = EblNavyDark)
            Text("Real-time STC production compared to assigned monthly target", fontSize = 11.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))

            // Table Header
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .background(EblNavyDark.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                .padding(vertical = 6.dp, horizontal = 8.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text("RM Name / Code", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EblNavyDark, modifier = Modifier.weight(2f))
              Text("STC / Target", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EblNavyDark, modifier = Modifier.weight(1.2f))
              Text("Achievement", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EblNavyDark, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(4.dp))

            rmPerformanceList.sortedByDescending { it.stats.stc }.forEach { rmRow ->
              val totalTarget = (rmRow.target?.creditCardTarget ?: 15) + (rmRow.target?.corporateCardTarget ?: 5) + (rmRow.target?.b2bTarget ?: 2)
              val percent = if (totalTarget > 0) ((rmRow.stats.stc.toFloat() / totalTarget.toFloat()) * 100).toInt() else 0

              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(vertical = 8.dp, horizontal = 8.dp)
              ) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Column(modifier = Modifier.weight(2f)) {
                    Text(rmRow.rmName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = EblNavyDark)
                    Text("Code: ${rmRow.rmCode} | Total: ${rmRow.stats.totalFiles}", fontSize = 10.sp, color = Color.Gray)
                  }
                  Text(
                    "${rmRow.stats.stc} / $totalTarget",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A),
                    modifier = Modifier.weight(1.2f)
                  )
                  Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (percent >= 100) Color(0xFFDCFCE7) else if (percent >= 50) Color(0xFFFEF3C7) else Color(0xFFFEE2E2),
                    modifier = Modifier.weight(1f)
                  ) {
                    Text(
                      text = "$percent%",
                      color = if (percent >= 100) Color(0xFF166534) else if (percent >= 50) Color(0xFF92400E) else Color(0xFF991B1B),
                      fontSize = 11.sp,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                  progress = { (percent.coerceIn(0, 100) / 100f) },
                  modifier = Modifier.fillMaxWidth().height(4.dp),
                  color = if (percent >= 100) Color(0xFF16A34A) else EblNavyPrimary,
                  trackColor = Color(0xFFE2E8F0)
                )
              }
              HorizontalDivider(color = Color(0xFFF1F5F9))
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
private fun SummaryKpiBox(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(8.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Column(
      modifier = Modifier.padding(8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
      Spacer(modifier = Modifier.height(2.dp))
      Text(text = title, fontSize = 9.sp, color = Color.Gray, maxLines = 1, softWrap = false)
    }
  }
}

@Composable
private fun ProductStatPill(title: String, total: Int, stc: Int, color: Color) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Surface(
      shape = RoundedCornerShape(6.dp),
      color = color.copy(alpha = 0.12f),
      modifier = Modifier.padding(horizontal = 4.dp)
    ) {
      Column(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Spacer(modifier = Modifier.height(2.dp))
        Text("Total: $total | STC: $stc", fontSize = 10.sp, color = Color.DarkGray)
      }
    }
  }
}
