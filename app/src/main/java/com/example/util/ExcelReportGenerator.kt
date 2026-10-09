package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CustomerFileEntity
import com.example.data.model.UserEntity
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

object ExcelReportGenerator {
  fun generateExcelReport(
    context: Context,
    files: List<CustomerFileEntity>,
    currentUser: UserEntity,
    periodLabel: String,
    customAppName: String
  ): File {
    val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
    val excelFile = File(reportsDir, "EBL_Report_${System.currentTimeMillis()}.xls")
    val sb = StringBuilder()
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
    sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n")
    sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
    sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n")
    sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n")
    sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\n")
    sb.append(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n")

    sb.append(" <Styles>\n")
    sb.append("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\n")
    sb.append("   <Alignment ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#333333\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"HeaderMain\">\n")
    sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"16\" ss:Bold=\"1\" ss:Color=\"#0A192F\"/>\n")
    sb.append("   <Interior ss:Color=\"#E8EEF5\" ss:Pattern=\"Solid\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"HeaderSub\">\n")
    sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Color=\"#475569\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"ColHeader\">\n")
    sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"11\" ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n")
    sb.append("   <Interior ss:Color=\"#1E3A8A\" ss:Pattern=\"Solid\"/>\n")
    sb.append("   <Borders>\n")
    sb.append("    <Border ss:Position=\"Bottom\" ss:LineStyle=\"Continuous\" ss:Weight=\"1\" ss:Color=\"#000000\"/>\n")
    sb.append("   </Borders>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"RowEven\">\n")
    sb.append("   <Alignment ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#1E293B\"/>\n")
    sb.append("   <Interior ss:Color=\"#F8FAFC\" ss:Pattern=\"Solid\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"RowOdd\">\n")
    sb.append("   <Alignment ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Color=\"#1E293B\"/>\n")
    sb.append("   <Interior ss:Color=\"#FFFFFF\" ss:Pattern=\"Solid\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"StatusSTC\">\n")
    sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#065F46\"/>\n")
    sb.append("   <Interior ss:Color=\"#D1FAE5\" ss:Pattern=\"Solid\"/>\n")
    sb.append("  </Style>\n")
    sb.append("  <Style ss:ID=\"StatusApproved\">\n")
    sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n")
    sb.append("   <Font ss:FontName=\"Segoe UI\" ss:Size=\"10\" ss:Bold=\"1\" ss:Color=\"#166534\"/>\n")
    sb.append("   <Interior ss:Color=\"#DCFCE7\" ss:Pattern=\"Solid\"/>\n")
    sb.append("  </Style>\n")
    sb.append(" </Styles>\n")

    sb.append(" <Worksheet ss:Name=\"Operations_Report\">\n")
    sb.append("  <Table ss:DefaultColumnWidth=\"100\">\n")
    sb.append("   <Column ss:Width=\"120\"/>\n")
    sb.append("   <Column ss:Width=\"160\"/>\n")
    sb.append("   <Column ss:Width=\"140\"/>\n")
    sb.append("   <Column ss:Width=\"110\"/>\n")
    sb.append("   <Column ss:Width=\"110\"/>\n")
    sb.append("   <Column ss:Width=\"110\"/>\n")
    sb.append("   <Column ss:Width=\"80\"/>\n")
    sb.append("   <Column ss:Width=\"100\"/>\n")
    sb.append("   <Column ss:Width=\"90\"/>\n")
    sb.append("   <Column ss:Width=\"130\"/>\n")
    sb.append("   <Column ss:Width=\"160\"/>\n")

    sb.append("   <Row ss:Height=\"30\">\n")
    sb.append("    <Cell ss:MergeAcross=\"10\" ss:StyleID=\"HeaderMain\"><Data ss:Type=\"String\">$customAppName - Operations &amp; File Performance Report</Data></Cell>\n")
    sb.append("   </Row>\n")
    sb.append("   <Row ss:Height=\"20\">\n")
    val subInfo = "Period: $periodLabel | Exported By: ${currentUser.name} (${currentUser.role} ${currentUser.rmCode}) | Total Records: ${files.size}"
    sb.append("    <Cell ss:MergeAcross=\"10\" ss:StyleID=\"HeaderSub\"><Data ss:Type=\"String\">$subInfo</Data></Cell>\n")
    sb.append("   </Row>\n")
    sb.append("   <Row ss:Height=\"10\"/>\n")

    sb.append("   <Row ss:Height=\"24\">\n")
    val headers = listOf(
      "File ID", "Customer Name", "Company Name", "Mobile",
      "Product Type", "Application Status", "Active Card", "CPV Status",
      "RM Officer", "Last Updated", "Remarks"
    )
    headers.forEach { h ->
      sb.append("    <Cell ss:StyleID=\"ColHeader\"><Data ss:Type=\"String\">$h</Data></Cell>\n")
    }
    sb.append("   </Row>\n")

    files.forEachIndexed { idx, f ->
      val styleId = if (f.applicationStatus.equals("STC", ignoreCase = true)) {
        "StatusSTC"
      } else if (f.applicationStatus.equals("Approved", ignoreCase = true)) {
        "StatusApproved"
      } else if (idx % 2 == 0) {
        "RowEven"
      } else {
        "RowOdd"
      }
      sb.append("   <Row ss:Height=\"20\">\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.fileId)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.customerName)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.companyName)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.mobile)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.productType)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.applicationStatus)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.activeStatus)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.cpvStatus)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.assignedRmCode)}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(DateUtils.formatDateTime(f.updatedAt))}</Data></Cell>\n")
      sb.append("    <Cell ss:StyleID=\"$styleId\"><Data ss:Type=\"String\">${escapeXml(f.remarks)}</Data></Cell>\n")
      sb.append("   </Row>\n")
    }
    sb.append("  </Table>\n")
    sb.append(" </Worksheet>\n")
    sb.append("</Workbook>\n")
    FileOutputStream(excelFile).use { fos ->
      OutputStreamWriter(fos, StandardCharsets.UTF_8).use { writer ->
        writer.write(sb.toString())
      }
    }
    return excelFile
  }

  fun shareOrViewExcel(context: Context, excelFile: File) {
    val uri: Uri = try {
      FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        excelFile
      )
    } catch (_: Exception) {
      Uri.fromFile(excelFile)
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "application/vnd.ms-excel"
      putExtra(Intent.EXTRA_STREAM, uri)
      putExtra(Intent.EXTRA_SUBJECT, "EBL Excel Performance Report")
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Open or Share Excel Report via").apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
  }

  fun generateAndShareExcelReport(
    context: Context,
    files: List<CustomerFileEntity>,
    currentUser: UserEntity? = null,
    timeFilterName: String = "All Time",
    generatedBy: String = "Bank Operations",
    customAppName: String = "RM File Management Suite"
  ): Boolean {
    return try {
      val user = currentUser ?: UserEntity(
        rmCode = "ADMIN",
        name = generatedBy,
        role = "ADMIN"
      )
      val file = generateExcelReport(context, files, user, timeFilterName, customAppName)
      shareOrViewExcel(context, file)
      true
    } catch (e: Exception) {
      false
    }
  }

  private fun escapeXml(text: String): String {
    return text.replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
  }
}
