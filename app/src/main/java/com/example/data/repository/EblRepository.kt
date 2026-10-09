package com.example.data.repository

import android.content.Context
import com.example.data.database.AppDatabase
import com.example.data.model.AppSettingEntity
import com.example.data.model.AuditLogEntity
import com.example.data.model.CallLogEntity
import com.example.data.model.CustomerFileEntity
import com.example.data.model.FileAttachmentEntity
import com.example.data.model.ImportantDocumentEntity
import com.example.data.model.RmTargetEntity
import com.example.data.model.SmsNotificationEntity
import com.example.data.model.SyncStatusEntity
import com.example.data.model.UserEntity
import com.example.util.DateUtils
import com.example.util.NotificationHelper
import com.example.util.SecurityUtils
import com.example.util.SmsService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class EblRepository(
  private val database: AppDatabase,
  private val authRepository: AuthRepository,
  private val context: Context? = null
) {
  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val httpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

  // 1. Customer Files Access Control
  @OptIn(ExperimentalCoroutinesApi::class)
  fun getAuthorizedFilesFlow(): Flow<List<CustomerFileEntity>> {
    return authRepository.currentUser.flatMapLatest { currentUser ->
      if (currentUser == null) {
        database.customerFileDao().getAllActiveFilesFlow()
      } else if (currentUser.role == "RM") {
        database.customerFileDao().getFilesForRmFlow(currentUser.rmCode)
      } else {
        database.customerFileDao().getAllActiveFilesFlow()
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  fun getAllFilesIncludingDeletedFlow(): Flow<List<CustomerFileEntity>> {
    return authRepository.currentUser.flatMapLatest { currentUser ->
      if (currentUser == null) {
        database.customerFileDao().getAllFilesIncludingDeletedFlow()
      } else if (currentUser.role == "MENTOR") {
        database.customerFileDao().getAllFilesIncludingDeletedFlow()
      } else if (currentUser.role == "RM") {
        database.customerFileDao().getFilesForRmFlow(currentUser.rmCode)
      } else {
        database.customerFileDao().getAllActiveFilesFlow()
      }
    }
  }

  // RM Target vs Achievement
  fun getTargetForRmFlow(rmCode: String): Flow<RmTargetEntity?> {
    return database.rmTargetDao().getTargetForRmFlow(rmCode)
  }

  suspend fun getTargetForRm(rmCode: String): RmTargetEntity = withContext(Dispatchers.IO) {
    database.rmTargetDao().getTargetForRm(rmCode) ?: RmTargetEntity(rmCode = rmCode)
  }

  suspend fun setRmTargets(
    rmCode: String,
    creditCardTarget: Int,
    corporateCardTarget: Int,
    b2bTarget: Int
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val now = DateUtils.currentDhakaMillis()
    val target = RmTargetEntity(
      rmCode = rmCode,
      creditCardTarget = creditCardTarget,
      corporateCardTarget = corporateCardTarget,
      b2bTarget = b2bTarget,
      updatedAt = now
    )
    database.rmTargetDao().insertOrUpdateTarget(target)

    val currentUser = authRepository.currentUser.value
    if (currentUser != null && (currentUser.role == "ADMIN" || currentUser.role == "MENTOR")) {
      val targetRmUser = database.userDao().getUser(rmCode)
      val rmMobile = targetRmUser?.mobile ?: ""
      val rmName = targetRmUser?.name ?: rmCode
      val formattedTime = DateUtils.formatDateTime(now)
      val smsMessage = "[EBL Alert] Dear $rmName ($rmCode), your sales target was updated by ${currentUser.role} (${currentUser.rmCode}). Targets -> CC: $creditCardTarget, Corp: $corporateCardTarget, B2B: $b2bTarget. Timestamp: $formattedTime. EBL Sales Suite."
      var smsStatus = "DELIVERED"
      if (context != null && rmMobile.isNotBlank()) {
        val sentHardware = SmsService.sendSms(context, rmMobile, smsMessage)
        smsStatus = if (sentHardware) "DELIVERED" else "SENT_IN_APP"
      }
      database.smsNotificationDao().insertSms(
        SmsNotificationEntity(
          recipientRmCode = rmCode,
          recipientMobile = rmMobile,
          recipientName = rmName,
          triggeredByRole = currentUser.role,
          triggeredByCode = currentUser.rmCode,
          actionType = "TARGET",
          targetType = "RM_TARGET",
          fileId = null,
          customerName = null,
          messageText = smsMessage,
          sentTimestamp = now,
          status = smsStatus,
          isRead = false
        )
      )
    }
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  // SMS Notifications
  fun getSmsForRmFlow(rmCode: String): Flow<List<SmsNotificationEntity>> {
    return database.smsNotificationDao().getSmsForRmFlow(rmCode)
  }

  fun getAllSmsFlow(): Flow<List<SmsNotificationEntity>> {
    return database.smsNotificationDao().getAllSmsFlow()
  }

  fun getUnreadSmsCountFlow(rmCode: String): Flow<Int> {
    return database.smsNotificationDao().getUnreadSmsCountFlow(rmCode)
  }

  suspend fun markSmsAsRead(id: Long) = withContext(Dispatchers.IO) {
    database.smsNotificationDao().markAsRead(id)
  }

  suspend fun markAllAsReadForRm(rmCode: String) = withContext(Dispatchers.IO) {
    database.smsNotificationDao().markAllAsReadForRm(rmCode)
  }

  suspend fun deleteSms(id: Long) = withContext(Dispatchers.IO) {
    database.smsNotificationDao().deleteSms(id)
    // Record in deleted_sms_ids so Google Sheets deletes it as well
    val currentDel = database.appSettingDao().getSetting("deleted_sms_ids")?.settingValue ?: ""
    val updatedDel = if (currentDel.isBlank()) id.toString() else "$currentDel,$id"
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("deleted_sms_ids", updatedDel, "USER", DateUtils.currentDhakaMillis())
    )
    applicationScope.launch { triggerGoogleSheetsSync() }
  }

  suspend fun clearSmsForRm(rmCode: String) = withContext(Dispatchers.IO) {
    val mySms = database.smsNotificationDao().getAllSms().filter { it.recipientRmCode == rmCode }
    val delIds = mySms.map { it.id }.joinToString(",")
    database.smsNotificationDao().clearSmsForRm(rmCode)
    if (delIds.isNotBlank()) {
      val currentDel = database.appSettingDao().getSetting("deleted_sms_ids")?.settingValue ?: ""
      val updatedDel = if (currentDel.isBlank()) delIds else "$currentDel,$delIds"
      database.appSettingDao().insertOrUpdateSetting(
        AppSettingEntity("deleted_sms_ids", updatedDel, rmCode, DateUtils.currentDhakaMillis())
      )
    }
    applicationScope.launch { triggerGoogleSheetsSync() }
  }

  suspend fun clearAllSms() = withContext(Dispatchers.IO) {
    val all = database.smsNotificationDao().getAllSms()
    val delIds = all.map { it.id }.joinToString(",")
    database.smsNotificationDao().clearAllSms()
    if (delIds.isNotBlank()) {
      val currentDel = database.appSettingDao().getSetting("deleted_sms_ids")?.settingValue ?: ""
      val updatedDel = if (currentDel.isBlank()) delIds else "$currentDel,$delIds"
      database.appSettingDao().insertOrUpdateSetting(
        AppSettingEntity("deleted_sms_ids", updatedDel, "ADMIN", DateUtils.currentDhakaMillis())
      )
    }
    applicationScope.launch { triggerGoogleSheetsSync() }
  }

  // Call Logs
  fun getAllCallLogsFlow(): Flow<List<CallLogEntity>> {
    return database.communicationDao().getAllCallLogsFlow()
  }

  suspend fun recordCallLog(
    recipientRmCode: String,
    recipientName: String,
    recipientMobile: String,
    callType: String = "INTERNET_VOICE",
    durationSeconds: Int = 0,
    status: String = "COMPLETED"
  ) = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
    val now = DateUtils.currentDhakaMillis()
    val call = CallLogEntity(
      callId = "CALL-${System.currentTimeMillis()}-${SecurityUtils.generateUniqueId().take(4)}",
      callerRmCode = currentUser?.rmCode ?: "SYSTEM",
      callerName = currentUser?.name ?: "EBL Officer",
      recipientRmCode = recipientRmCode,
      recipientName = recipientName,
      recipientMobile = recipientMobile,
      callType = callType,
      durationSeconds = durationSeconds,
      timestamp = now,
      status = status
    )
    database.communicationDao().insertCallLog(call)
    applicationScope.launch { triggerGoogleSheetsSync() }
  }

  fun getFileByIdFlow(fileId: String): Flow<CustomerFileEntity?> {
    return database.customerFileDao().getFileByIdFlow(fileId)
  }

  suspend fun getFileById(fileId: String): CustomerFileEntity? = withContext(Dispatchers.IO) {
    database.customerFileDao().getFileById(fileId) ?: database.customerFileDao().getFileByAnyId(fileId)
  }

  suspend fun getFileByAnyId(id: String): CustomerFileEntity? = withContext(Dispatchers.IO) {
    database.customerFileDao().getFileByAnyId(id) ?: database.customerFileDao().getFileById(id)
  }

  suspend fun saveCustomerFile(
    fileId: String?,
    customerName: String,
    companyName: String,
    officeAddress: String,
    mobile: String,
    altMobile: String,
    email: String,
    productType: String,
    applicationStatus: String,
    activeStatus: String,
    assignedRmCode: String,
    ccNumber: String = "",
    pendingDocuments: List<String>,
    remarks: String,
    cpvStatus: String,
    cpvDate: String,
    cpvAddress: String,
    cpvRemarks: String,
    cpvPhotoUri: String = "",
    cpvSupportingDocUri: String = "",
    submissionLatitude: Double? = null,
    submissionLongitude: Double? = null,
    submissionAddress: String? = null,
    serialNumber: String? = null
  ): Result<CustomerFileEntity> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized operation."))

    val resolvedRmCode = (if (currentUser.role == "RM") {
      currentUser.rmCode
    } else {
      assignedRmCode.ifBlank { currentUser.rmCode }
    }).trim().uppercase()

    val now = DateUtils.currentDhakaMillis()
    val cleanId = fileId?.trim().orEmpty()
    val cleanCc = ccNumber.trim()

    val existing = if (cleanId.isNotBlank()) {
      database.customerFileDao().getFileById(cleanId)
        ?: database.customerFileDao().getFileByAnyId(cleanId)
        ?: (if (cleanCc.isNotBlank()) database.customerFileDao().getFileByAnyId(cleanCc) else null)
    } else if (cleanCc.isNotBlank()) {
      database.customerFileDao().getFileByAnyId(cleanCc)
    } else null

    val targetFileId = existing?.fileId ?: (if (cleanId.isNotBlank()) cleanId else SecurityUtils.generateFileId(resolvedRmCode))
    val isNew = existing == null

    val cleanMobile = mobile.trim()
    val normalizedMobile = cleanMobile.replace(Regex("[^0-9]"), "").removePrefix("88").trimStart('0')
    if (normalizedMobile.length >= 6) {
      val allActive = database.customerFileDao().getAllActiveFiles()
      val duplicate = allActive.firstOrNull { f ->
        !f.isDeleted && f.fileId != targetFileId && (
          f.mobile.trim().equals(cleanMobile, ignoreCase = true) ||
          f.mobile.replace(Regex("[^0-9]"), "").removePrefix("88").trimStart('0') == normalizedMobile ||
          (f.altMobile.isNotBlank() && f.altMobile.replace(Regex("[^0-9]"), "").removePrefix("88").trimStart('0') == normalizedMobile)
        )
      }
      if (duplicate != null) {
        val dupIdentifier = duplicate.ccNumber.ifBlank { duplicate.fileId }
        return@withContext Result.failure(
          Exception("This number already exists! Mobile '$cleanMobile' is already registered with Customer '${duplicate.customerName}' ($dupIdentifier). Duplicate mobile numbers are strictly prohibited.")
        )
      }
    }

    val allActiveFiles = database.customerFileDao().getAllActiveFiles()
    val resolvedSl = if (!serialNumber.isNullOrBlank()) {
      serialNumber.trim()
    } else if (existing != null && existing.serialNumber.isNotBlank()) {
      existing.serialNumber
    } else {
      val maxSl = allActiveFiles.mapNotNull { it.serialNumber.toIntOrNull() }.maxOrNull() ?: allActiveFiles.size
      (maxSl + 1).toString()
    }

    if (existing != null && currentUser.role == "RM" && !existing.assignedRmCode.trim().equals(currentUser.rmCode.trim(), ignoreCase = true)) {
      return@withContext Result.failure(Exception("You do not have permission to modify this record."))
    }

    val createdTimestamp = existing?.createdAt ?: now
    val createdBy = existing?.createdBy ?: currentUser.rmCode
    val submittedAt = if (applicationStatus.equals("Submitted", ignoreCase = true)) {
      existing?.submittedAt ?: now
    } else existing?.submittedAt

    val approvedAt = if (applicationStatus.equals("Approved", ignoreCase = true)) {
      existing?.approvedAt ?: now
    } else existing?.approvedAt

    val pendingDocsJoined = pendingDocuments.joinToString(",")
    val resolvedSubmissionLat = submissionLatitude ?: existing?.submissionLatitude
    val resolvedSubmissionLng = submissionLongitude ?: existing?.submissionLongitude
    val resolvedSubmissionAddr = submissionAddress ?: existing?.submissionAddress

    val entity = CustomerFileEntity(
      fileId = targetFileId,
      customerName = customerName.trim(),
      companyName = companyName.trim(),
      officeAddress = officeAddress.trim(),
      mobile = mobile.trim(),
      altMobile = altMobile.trim(),
      email = email.trim(),
      productType = productType,
      applicationStatus = applicationStatus,
      activeStatus = activeStatus,
      assignedRmCode = resolvedRmCode,
      ccNumber = ccNumber.trim(),
      pendingDocuments = pendingDocsJoined,
      remarks = remarks.trim(),
      cpvStatus = cpvStatus,
      cpvDate = cpvDate,
      cpvAddress = cpvAddress.trim(),
      cpvRemarks = cpvRemarks.trim(),
      cpvPhotoUri = cpvPhotoUri.ifBlank { existing?.cpvPhotoUri ?: "" },
      cpvSupportingDocUri = cpvSupportingDocUri.ifBlank { existing?.cpvSupportingDocUri ?: "" },
      cpvLastUpdatedBy = currentUser.rmCode,
      submissionLatitude = resolvedSubmissionLat,
      submissionLongitude = resolvedSubmissionLng,
      submissionAddress = resolvedSubmissionAddr,
      serialNumber = resolvedSl,
      createdAt = createdTimestamp,
      updatedAt = now,
      submittedAt = submittedAt,
      approvedAt = approvedAt,
      createdBy = createdBy,
      updatedBy = currentUser.rmCode,
      isDeleted = false,
      isSynced = false
    )

    if (isNew || existing == null) {
      database.customerFileDao().insertFile(entity)
    } else {
      database.customerFileDao().updateFile(entity)
      if ((currentUser.role == "MENTOR" || currentUser.role == "ADMIN") && (existing.assignedRmCode != currentUser.rmCode || existing.assignedRmCode != resolvedRmCode)) {
        val hasRmChanged = !existing.assignedRmCode.equals(resolvedRmCode, ignoreCase = true)
        val changedItems = mutableListOf<String>()
        if (hasRmChanged) changedItems.add("Assigned RM Reassigned -> $resolvedRmCode")
        if (existing.applicationStatus != applicationStatus) changedItems.add("Status -> $applicationStatus")
        if (existing.activeStatus != activeStatus) changedItems.add("Active -> $activeStatus")
        if (existing.remarks != remarks.trim()) changedItems.add("Remarks updated")
        if (existing.cpvStatus != cpvStatus) changedItems.add("CPV -> $cpvStatus")
        if (existing.ccNumber != ccNumber.trim()) changedItems.add("CC Number -> $ccNumber")
        if (changedItems.isEmpty()) changedItems.add("File details modified by ${currentUser.role}")

        val changeDetailsStr = changedItems.joinToString(", ")
        val targetRmUser = database.userDao().getUser(resolvedRmCode)
        val rmMobile = targetRmUser?.mobile ?: ""
        val rmName = targetRmUser?.name ?: resolvedRmCode
        val formattedTime = DateUtils.formatDateTime(now)
        val smsMessage = if (hasRmChanged) {
          "[EBL Alert] Dear $rmName ($resolvedRmCode), customer file $targetFileId for '$customerName' has been REASSIGNED to you by ${currentUser.role} (${currentUser.name.ifBlank { currentUser.rmCode }}). Timestamp: $formattedTime. EBL Sales Suite."
        } else {
          "[EBL Alert] Dear $rmName ($resolvedRmCode), customer file $targetFileId for '$customerName' was UPDATED by ${currentUser.role} (${currentUser.name.ifBlank { currentUser.rmCode }}). Changes: $changeDetailsStr. Timestamp: $formattedTime. EBL Sales Suite."
        }
        var smsStatus = "DELIVERED"
        if (context != null && rmMobile.isNotBlank()) {
          val sentHardware = SmsService.sendSms(context, rmMobile, smsMessage)
          smsStatus = if (sentHardware) "DELIVERED" else "SENT_IN_APP"
        }
        database.smsNotificationDao().insertSms(
          SmsNotificationEntity(
            recipientRmCode = resolvedRmCode,
            recipientMobile = rmMobile,
            recipientName = rmName,
            triggeredByRole = currentUser.role,
            triggeredByCode = currentUser.rmCode,
            actionType = if (hasRmChanged) "TRANSFER" else "UPDATE",
            targetType = "CUSTOMER_FILE",
            fileId = targetFileId,
            customerName = customerName,
            messageText = smsMessage,
            sentTimestamp = now,
            status = smsStatus,
            isRead = false
          )
        )
      }
    }

    if (resolvedSubmissionLat != null && resolvedSubmissionLng != null) {
      database.userLocationLogDao().insertLocationLog(
        com.example.data.model.UserLocationLogEntity(
          rmCode = resolvedRmCode,
          userName = currentUser.name,
          latitude = resolvedSubmissionLat,
          longitude = resolvedSubmissionLng,
          address = resolvedSubmissionAddr ?: "Auto-Captured via File Entry",
          sourceAction = "CUSTOMER_FILE_ENTRY",
          timestamp = now
        )
      )
      database.userDao().updateLocation(
        rmCode = resolvedRmCode,
        lat = resolvedSubmissionLat,
        lng = resolvedSubmissionLng,
        address = resolvedSubmissionAddr ?: "Auto-Captured via File Entry",
        time = now
      )
    }

    updatePendingSyncCount()
    applicationScope.launch {
      triggerGoogleSheetsSync()
    }
    Result.success(entity)
  }

  suspend fun softDeleteCustomerFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val file = database.customerFileDao().getFileById(fileId)
      ?: return@withContext Result.failure(Exception("File not found."))
    if (currentUser.role == "RM" && file.assignedRmCode != currentUser.rmCode) {
      return@withContext Result.failure(Exception("Unauthorized to delete this record."))
    }
    val now = DateUtils.currentDhakaMillis()
    database.customerFileDao().softDeleteFile(fileId, currentUser.rmCode, now)

    val currentDel = database.appSettingDao().getSetting("deleted_file_ids")?.settingValue ?: ""
    val toAdd = listOfNotNull(file.fileId.ifBlank { null }, file.ccNumber.ifBlank { null }).joinToString(",")
    val updatedDel = if (currentDel.isBlank()) toAdd else "$currentDel,$toAdd"
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("deleted_file_ids", updatedDel, currentUser.rmCode, now)
    )

    updatePendingSyncCount()
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun restoreCustomerFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Admin or Mentor can restore deleted files."))
    }
    val now = DateUtils.currentDhakaMillis()
    database.customerFileDao().restoreFile(fileId, currentUser.rmCode, now)
    updatePendingSyncCount()
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun permanentDeleteCustomerFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Permanent deletion restricted strictly to Mentor role."))
    }
    val file = database.customerFileDao().getFileById(fileId)
    val now = DateUtils.currentDhakaMillis()
    database.customerFileDao().permanentDeleteFile(fileId)

    val currentDel = database.appSettingDao().getSetting("deleted_file_ids")?.settingValue ?: ""
    val toAdd = listOfNotNull(fileId, file?.ccNumber?.ifBlank { null }).joinToString(",")
    val updatedDel = if (currentDel.isBlank()) toAdd else "$currentDel,$toAdd"
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("deleted_file_ids", updatedDel, currentUser.rmCode, now)
    )

    updatePendingSyncCount()
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  // 2. Attachments
  fun getAttachmentsForFileFlow(fileId: String): Flow<List<FileAttachmentEntity>> {
    return database.fileAttachmentDao().getAttachmentsForFileFlow(fileId)
  }

  suspend fun addAttachment(
    fileId: String,
    category: String,
    fileName: String,
    fileType: String,
    fileSizeBytes: Long,
    fileUri: String
  ): Result<FileAttachmentEntity> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val attachmentId = "ATT-${SecurityUtils.generateUniqueId().take(8)}"
    val now = DateUtils.currentDhakaMillis()
    val attachment = FileAttachmentEntity(
      attachmentId = attachmentId,
      fileId = fileId,
      category = category,
      fileName = fileName,
      fileType = fileType,
      storagePath = "files/${currentUser.rmCode}/$fileId/$fileName",
      fileUri = fileUri,
      fileSizeBytes = fileSizeBytes,
      uploadedBy = currentUser.rmCode,
      uploadedAt = now
    )
    database.fileAttachmentDao().insertAttachment(attachment)
    Result.success(attachment)
  }

  suspend fun deleteAttachment(attachmentId: String, fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
    database.fileAttachmentDao().deleteAttachment(attachmentId)
    Result.success(Unit)
  }

  // 3. RM Management
  fun getAllRmsFlow(): Flow<List<UserEntity>> {
    return database.userDao().getAllRmsFlow()
  }

  suspend fun createRm(
    rmCode: String,
    name: String,
    mobile: String,
    email: String,
    officeAddress: String,
    initialPassword: String
  ): Result<UserEntity> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Access denied."))
    }
    val cleanRmCode = rmCode.trim()
    if (database.userDao().getUser(cleanRmCode) != null) {
      return@withContext Result.failure(Exception("RM Code '$cleanRmCode' already exists."))
    }
    val now = DateUtils.currentDhakaMillis()
    val salt = SecurityUtils.generateSalt()
    val hash = SecurityUtils.hashPassword(initialPassword.trim(), salt)
    val newUser = UserEntity(
      rmCode = cleanRmCode,
      name = name.trim(),
      role = "RM",
      passwordHash = hash,
      salt = salt,
      mobile = mobile.trim(),
      email = email.trim(),
      officeAddress = officeAddress.trim(),
      accountStatus = "ACTIVE",
      mustChangePassword = false,
      createdAt = now,
      authUid = "AUTH_RM_$cleanRmCode"
    )
    database.userDao().insertUser(newUser)
    database.rmTargetDao().insertOrUpdateTarget(
      RmTargetEntity(
        rmCode = cleanRmCode,
        creditCardTarget = 15,
        corporateCardTarget = 5,
        b2bTarget = 2,
        updatedAt = now
      )
    )
    applicationScope.launch {
      syncRmPasswordToGoogleSheets(cleanRmCode, hash, salt)
      triggerGoogleSheetsSync()
    }
    Result.success(newUser)
  }

  suspend fun updateRm(
    rmCode: String,
    name: String,
    mobile: String,
    email: String,
    officeAddress: String,
    newPassword: String? = null
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Access denied."))
    }
    val existing = database.userDao().getUser(rmCode)
      ?: return@withContext Result.failure(Exception("RM user not found."))

    val targetStatus = existing.accountStatus
    val updated = if (!newPassword.isNullOrBlank()) {
      val salt = SecurityUtils.generateSalt()
      val hash = SecurityUtils.hashPassword(newPassword.trim(), salt)
      existing.copy(
        name = name.trim(),
        mobile = mobile.trim(),
        email = email.trim(),
        officeAddress = officeAddress.trim(),
        passwordHash = hash,
        salt = salt,
        mustChangePassword = false
      )
    } else {
      existing.copy(
        name = name.trim(),
        mobile = mobile.trim(),
        email = email.trim(),
        officeAddress = officeAddress.trim(),
        accountStatus = targetStatus
      )
    }
    database.userDao().updateUser(updated)
    if (!newPassword.isNullOrBlank()) {
      val now = DateUtils.currentDhakaMillis()
      database.appSettingDao().insertOrUpdateSetting(
        AppSettingEntity("RM_PASS_UPDATED_AT_${rmCode.trim().uppercase()}", now.toString(), currentUser.rmCode, now)
      )
      applicationScope.launch {
        syncRmPasswordToGoogleSheets(rmCode, updated.passwordHash, updated.salt)
      }
    }
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun approveRm(rmCode: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Mentor can approve RM accounts."))
    }
    database.userDao().updateStatus(rmCode, "ACTIVE")
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun rejectRm(rmCode: String, reason: String = ""): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Mentor can reject RM accounts."))
    }
    database.userDao().updateStatus(rmCode, "INACTIVE")
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun syncRmPasswordToGoogleSheets(rmCode: String, passwordHash: String, salt: String): Unit = withContext(Dispatchers.IO) {
    try {
      val currentStatus = database.appSettingDao().getSyncStatus() ?: return@withContext
      val activeWebAppUrl = currentStatus.appsScriptUrl.ifBlank {
        "https://script.google.com/macros/s/AKfycbzxQ2GtKwhT8UjUdvqPTWielndlsMu9d_rVFf2ro4sI5-uCRrvj8uQXFKpVnBF7g9r0NQ/exec"
      }
      if (activeWebAppUrl.isBlank() || !activeWebAppUrl.startsWith("http")) return@withContext
      val payload = JSONObject().apply {
        put("action", "UPDATE_RM_PASSWORD")
        put("rmCode", rmCode.trim().uppercase())
        put("passwordHash", passwordHash)
        put("salt", salt)
        put("updatedAt", DateUtils.formatDateTime(DateUtils.currentDhakaMillis()))
        put("spreadsheetId", currentStatus.spreadsheetId)
        put("secretKey", currentStatus.syncSecretKey)
      }
      val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
      val request = Request.Builder().url(activeWebAppUrl).post(requestBody).build()
      httpClient.newCall(request).execute().use { resp ->
        resp.body?.string()
      }
    } catch (_: Exception) {}
  }

  suspend fun resetRmPassword(rmCode: String, newPassword: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Access denied."))
    }
    val now = DateUtils.currentDhakaMillis()
    val salt = SecurityUtils.generateSalt()
    val hash = SecurityUtils.hashPassword(newPassword, salt)
    database.userDao().updatePassword(rmCode, hash, salt, mustChange = false)
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("RM_PASS_UPDATED_AT_${rmCode.trim().uppercase()}", now.toString(), currentUser.rmCode, now)
    )
    applicationScope.launch {
      syncRmPasswordToGoogleSheets(rmCode, hash, salt)
      triggerGoogleSheetsSync()
    }
    Result.success(Unit)
  }

  suspend fun deleteRmProfile(rmCode: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Admin or Mentor can delete RM profiles."))
    }
    val now = DateUtils.currentDhakaMillis()
    database.userDao().deleteUser(rmCode)
    database.rmTargetDao().deleteTargetForRm(rmCode)

    val currentDel = database.appSettingDao().getSetting("deleted_rm_codes")?.settingValue ?: ""
    val updatedDel = if (currentDel.isBlank()) rmCode else "$currentDel,$rmCode"
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("deleted_rm_codes", updatedDel, currentUser.rmCode, now)
    )

    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun reassignCustomerFileRm(fileId: String, newRmCode: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Admin or Mentor can change RM assignment."))
    }
    val file = database.customerFileDao().getFileByAnyId(fileId)
      ?: return@withContext Result.failure(Exception("Customer file not found."))
    val oldRmCode = file.assignedRmCode
    if (oldRmCode.equals(newRmCode, ignoreCase = true)) {
      return@withContext Result.success(Unit)
    }
    val now = DateUtils.currentDhakaMillis()
    val updated = file.copy(
      assignedRmCode = newRmCode.trim().uppercase(),
      updatedAt = now,
      updatedBy = "${currentUser.role}_${currentUser.rmCode}",
      isSynced = false
    )
    database.customerFileDao().updateFile(updated)
    updatePendingSyncCount()
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun saveUniversalChecklistSettings(
    headerTemplate: String,
    regardsTemplate: String,
    corporateDocsJson: String = "",
    enhancementDocsJson: String = ""
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Admin / Mentor can configure universal checklist templates."))
    }
    val now = DateUtils.currentDhakaMillis()
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("CHECKLIST_HEADER_TEMPLATE", headerTemplate, currentUser.rmCode, now)
    )
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("CHECKLIST_REGARDS_TEMPLATE", regardsTemplate, currentUser.rmCode, now)
    )
    if (corporateDocsJson.isNotBlank()) {
      database.appSettingDao().insertOrUpdateSetting(
        AppSettingEntity("CHECKLIST_CORP_DOCS", corporateDocsJson, currentUser.rmCode, now)
      )
    }
    if (enhancementDocsJson.isNotBlank()) {
      database.appSettingDao().insertOrUpdateSetting(
        AppSettingEntity("CHECKLIST_ENHANCE_DOCS", enhancementDocsJson, currentUser.rmCode, now)
      )
    }
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  fun getAppCustomNameFlow(): Flow<String> {
    return database.appSettingDao().getAllSettingsFlow().map { settings ->
      settings.find { it.settingKey == "app_custom_name" }?.settingValue ?: "RM File Management Suite"
    }
  }

  suspend fun setAppCustomName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    if (currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Only Mentor can configure the universal app name."))
    }
    val cleanName = name.trim().ifBlank { "RM File Management Suite" }
    val setting = AppSettingEntity(
      settingKey = "app_custom_name",
      settingValue = cleanName,
      updatedBy = currentUser.rmCode,
      updatedAt = DateUtils.currentDhakaMillis()
    )
    database.appSettingDao().insertOrUpdateSetting(setting)
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  suspend fun setRmStatus(rmCode: String, newStatus: String): Result<Unit> = withContext(Dispatchers.IO) {
    database.userDao().updateStatus(rmCode, newStatus)
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  fun getAuditLogsFlow(): Flow<List<AuditLogEntity>> {
    val currentUser = authRepository.currentUser.value ?: return emptyFlow()
    return if (currentUser.role == "RM") {
      database.auditLogDao().getLogsForRmFlow(currentUser.rmCode)
    } else {
      database.auditLogDao().getAllLogsFlow()
    }
  }

  fun getAppSettingsFlow(): Flow<List<AppSettingEntity>> {
    return database.appSettingDao().getAllSettingsFlow()
  }

  suspend fun updateAppSetting(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val setting = AppSettingEntity(
      settingKey = key,
      settingValue = value,
      updatedBy = currentUser.rmCode,
      updatedAt = DateUtils.currentDhakaMillis()
    )
    database.appSettingDao().insertOrUpdateSetting(setting)
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  fun getSyncStatusFlow(): Flow<SyncStatusEntity?> {
    return database.appSettingDao().getSyncStatusFlow()
  }

  private suspend fun updatePendingSyncCount() {
    val unsynced = database.customerFileDao().getUnsyncedFiles().size
    val current = database.appSettingDao().getSyncStatus() ?: SyncStatusEntity()
    database.appSettingDao().insertOrUpdateSyncStatus(current.copy(pendingRecordsCount = unsynced))
  }

  suspend fun setGoogleSheetUrl(urlOrId: String): Result<String> = withContext(Dispatchers.IO) {
    val clean = urlOrId.trim()
    val match = Regex("/spreadsheets/d/([a-zA-Z0-9-_]+)").find(clean)
    val extractedId = match?.groupValues?.get(1) ?: clean
    val current = database.appSettingDao().getSyncStatus() ?: SyncStatusEntity()
    val updated = current.copy(
      spreadsheetId = extractedId,
      lastSyncMessage = "Linked to Google Sheet ($extractedId). Real-time auto-sync active."
    )
    database.appSettingDao().insertOrUpdateSyncStatus(updated)
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(extractedId)
  }

  suspend fun updateAppsScriptConfig(url: String, secretKey: String): Result<Unit> = withContext(Dispatchers.IO) {
    val current = database.appSettingDao().getSyncStatus() ?: SyncStatusEntity()
    val updated = current.copy(
      appsScriptUrl = url.trim(),
      syncSecretKey = secretKey.trim()
    )
    database.appSettingDao().insertOrUpdateSyncStatus(updated)
    Result.success(Unit)
  }

  // Real-Time Google Sheets Synchronization (Includes Important Documents, Call Logs & Event Submissions!)
  suspend fun triggerGoogleSheetsSync(): Result<String> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
    val syncedBy = currentUser?.rmCode ?: "APP_BACKGROUND_SYNC"
    val currentStatus = database.appSettingDao().getSyncStatus() ?: SyncStatusEntity()
    val activeWebAppUrl = currentStatus.appsScriptUrl.ifBlank {
      "https://script.google.com/macros/s/AKfycbzxQ2GtKwhT8UjUdvqPTWielndlsMu9d_rVFf2ro4sI5-uCRrvj8uQXFKpVnBF7g9r0NQ/exec"
    }

    try {
      val allFiles = database.customerFileDao().getAllActiveFiles()
      val filesToPush = allFiles.filter { !it.isDeleted }.sortedWith(
        compareBy<CustomerFileEntity> { it.serialNumber.toIntOrNull() ?: Int.MAX_VALUE }
          .thenBy { it.createdAt }
      )
      val allUsers = database.userDao().getAllUsers()
      val allRms = allUsers.filter { it.role == "RM" }
      val allTargets = database.rmTargetDao().getAllTargets()
      val allSettings = database.appSettingDao().getAllSettings()
      val recentLogs = database.auditLogDao().getAllLogs().take(50)
      val allAttachments = database.fileAttachmentDao().getAllAttachments()
      val recentLocations = database.userLocationLogDao().getRecentLocationLogs(50)
      val recentSms = database.smsNotificationDao().getAllSms()
      val allDocs = database.importantDocumentDao().getAllActiveDocuments()
      val allCallLogs = database.communicationDao().getAllCallLogs()

      if (activeWebAppUrl.isNotBlank() && activeWebAppUrl.startsWith("http")) {
        val filesArray = JSONArray()
        for ((idx, f) in filesToPush.withIndex()) {
          filesArray.put(JSONObject().apply {
            put("serialNumber", f.serialNumber.ifBlank { (idx + 1).toString() })
            put("sl", f.serialNumber.ifBlank { (idx + 1).toString() })
            put("ccNumber", f.ccNumber.ifBlank { f.fileId })
            put("fileId", f.fileId)
            put("customerName", f.customerName)
            put("companyName", f.companyName)
            put("officeAddress", f.officeAddress)
            put("mobile", f.mobile)
            put("email", f.email)
            put("productType", f.productType)
            put("applicationStatus", f.applicationStatus)
            put("activeStatus", f.activeStatus)
            put("assignedRmCode", f.assignedRmCode)
            put("pendingDocuments", f.pendingDocuments)
            put("remarks", f.remarks)
            put("cpvRemarks", f.cpvRemarks)
            put("cpvStatus", f.cpvStatus)
            put("submissionAddress", f.submissionAddress ?: "")
            put("submissionLat", f.submissionLatitude ?: 0.0)
            put("submissionLng", f.submissionLongitude ?: 0.0)
            put("updatedAt", DateUtils.formatDateTime(f.updatedAt))
            put("updatedBy", f.updatedBy)
          })
        }

        val rmsArray = JSONArray()
        for (rm in allRms) {
          val target = allTargets.find { it.rmCode == rm.rmCode }
          rmsArray.put(JSONObject().apply {
            put("rmCode", rm.rmCode)
            put("name", rm.name)
            put("mobile", rm.mobile)
            put("email", rm.email)
            put("officeAddress", rm.officeAddress)
            put("role", rm.role)
            put("accountStatus", rm.accountStatus)
            put("creditCardTarget", target?.creditCardTarget ?: 15)
            put("corporateCardTarget", target?.corporateCardTarget ?: 5)
            put("b2bTarget", target?.b2bTarget ?: 2)
            put("passwordHash", rm.passwordHash)
            put("salt", rm.salt)
            put("createdAt", DateUtils.formatDateTime(rm.createdAt))
            put("updatedAt", DateUtils.formatDateTime(target?.updatedAt ?: rm.createdAt))
          })
        }

        val settingsArray = JSONArray()
        for (s in allSettings) {
          settingsArray.put(JSONObject().apply {
            put("settingKey", s.settingKey)
            put("settingValue", s.settingValue)
            put("description", "Universal Setting - Auto-synced across all mobile apps")
            put("updatedBy", s.updatedBy)
            put("updatedAt", DateUtils.formatDateTime(s.updatedAt))
          })
        }

        val smsArray = JSONArray()
        for (s in recentSms) {
          smsArray.put(JSONObject().apply {
            put("id", s.id)
            put("recipientRmCode", s.recipientRmCode)
            put("recipientName", s.recipientName)
            put("recipientMobile", s.recipientMobile)
            put("triggeredByRole", s.triggeredByRole)
            put("triggeredByCode", s.triggeredByCode)
            put("actionType", s.actionType)
            put("targetType", s.targetType)
            put("fileId", s.fileId ?: "")
            put("customerName", s.customerName ?: "")
            put("messageText", s.messageText)
            put("sentTimestamp", DateUtils.formatDateTime(s.sentTimestamp))
            put("status", s.status)
          })
        }

        // IMPORTANT DOCUMENTS ARRAY (Fix Requirement 7!)
        val docsArray = JSONArray()
        for (d in allDocs) {
          docsArray.put(JSONObject().apply {
            put("docId", d.docId)
            put("title", d.title)
            put("category", d.category)
            put("description", d.description)
            put("fileName", d.fileName)
            put("fileType", d.fileType)
            put("fileSize", d.fileSizeBytes)
            put("uploadedBy", d.uploadedBy)
            put("uploaderName", d.uploaderName)
            put("uploaderRole", d.uploaderRole)
            put("createdAt", DateUtils.formatDateTime(d.createdAt))
            put("updatedAt", DateUtils.formatDateTime(d.updatedAt))
          })
        }

        // CALL LOGS ARRAY (Fix Requirement 4!)
        val callLogsArray = JSONArray()
        for (c in allCallLogs) {
          callLogsArray.put(JSONObject().apply {
            put("callId", c.callId)
            put("callerRmCode", c.callerRmCode)
            put("callerName", c.callerName)
            put("recipientRmCode", c.recipientRmCode)
            put("recipientName", c.recipientName)
            put("recipientMobile", c.recipientMobile)
            put("callType", c.callType)
            put("durationSeconds", c.durationSeconds)
            put("timestamp", DateUtils.formatDateTime(c.timestamp))
            put("status", c.status)
          })
        }

        // Deleted records tracking
        val allDeletedFiles = database.customerFileDao().getAllDeletedFiles()
        val deletedFileIds = JSONArray()
        for (df in allDeletedFiles) {
          deletedFileIds.put(df.fileId)
          if (df.ccNumber.isNotBlank()) deletedFileIds.put(df.ccNumber)
        }
        val delFilesSetting = database.appSettingDao().getSetting("deleted_file_ids")?.settingValue ?: ""
        delFilesSetting.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
          deletedFileIds.put(it)
        }

        val delRmsSetting = database.appSettingDao().getSetting("deleted_rm_codes")?.settingValue ?: ""
        val deletedRmCodes = JSONArray()
        delRmsSetting.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.forEach {
          deletedRmCodes.put(it)
        }

        val delSmsSetting = database.appSettingDao().getSetting("deleted_sms_ids")?.settingValue ?: ""
        val deletedSmsIds = JSONArray()
        delSmsSetting.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach {
          deletedSmsIds.put(it)
        }

        val payload = JSONObject().apply {
          put("action", "SYNC_ALL_DATA")
          put("spreadsheetId", currentStatus.spreadsheetId.ifBlank { "1lb9Wou10ecl28EUgaXD2cA3YCNY7nNHp1BOFrrLezqI" })
          put("secretKey", currentStatus.syncSecretKey.ifBlank { "ebl_secure_sync_token_2026" })
          put("timestamp", DateUtils.currentDhakaMillis())
          put("syncedBy", syncedBy)
          put("filesCount", filesArray.length())
          put("files", filesArray)
          put("deletedFileIds", deletedFileIds)
          put("deletedRmCodes", deletedRmCodes)
          put("deletedSmsIds", deletedSmsIds)
          put("rms", rmsArray)
          put("settings", settingsArray)
          put("sms", smsArray)
          put("importantDocuments", docsArray)
          put("callLogs", callLogsArray)
        }

        val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(activeWebAppUrl).post(requestBody).build()
        try {
          httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
              val respBodyStr = response.body?.string() ?: ""
              if (respBodyStr.isNotBlank()) {
                val respJson = JSONObject(respBodyStr)
                respJson.optJSONArray("latestFiles")?.let { processSheetFiles(it) }
                  ?: respJson.optJSONArray("files")?.let { processSheetFiles(it) }
                respJson.optJSONArray("rms")?.let { processSheetRms(it) }
                respJson.optJSONArray("settings")?.let { processSheetSettings(it) }
                respJson.optJSONArray("importantDocuments")?.let { processSheetImportantDocs(it) }
              }
            }
          }
        } catch (_: Exception) {}
      }

      val pushedIds = filesToPush.map { it.fileId }
      if (pushedIds.isNotEmpty()) {
        database.customerFileDao().markFilesSynced(pushedIds)
      }
      val now = DateUtils.currentDhakaMillis()
      val successMsg = "Synchronized files, RMs, Important Documents & Settings to Spreadsheet."
      database.appSettingDao().insertOrUpdateSyncStatus(
        currentStatus.copy(
          appsScriptUrl = activeWebAppUrl,
          lastSyncTimestamp = now,
          lastSyncStatus = "SUCCESS",
          lastSyncMessage = successMsg,
          pendingRecordsCount = 0
        )
      )
      Result.success(successMsg)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  suspend fun pullDataFromGoogleSheets(): Result<String> = withContext(Dispatchers.IO) {
    val currentStatus = database.appSettingDao().getSyncStatus() ?: SyncStatusEntity()
    val activeWebAppUrl = currentStatus.appsScriptUrl.ifBlank {
      "https://script.google.com/macros/s/AKfycbzxQ2GtKwhT8UjUdvqPTWielndlsMu9d_rVFf2ro4sI5-uCRrvj8uQXFKpVnBF7g9r0NQ/exec"
    }
    try {
      val payload = JSONObject().apply {
        put("action", "FETCH_SHEET_DATA")
        put("spreadsheetId", currentStatus.spreadsheetId)
        put("secretKey", currentStatus.syncSecretKey)
      }
      val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
      val request = Request.Builder().url(activeWebAppUrl).post(requestBody).build()
      val respStr = httpClient.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}: ${response.message}")
        response.body?.string() ?: ""
      }
      val json = JSONObject(respStr)
      val filesArray = json.optJSONArray("files") ?: json.optJSONArray("latestFiles") ?: JSONArray()
      val updatedFilesCount = processSheetFiles(filesArray)
      val rmsArray = json.optJSONArray("rms") ?: JSONArray()
      val updatedRmsCount = processSheetRms(rmsArray)
      val settingsArray = json.optJSONArray("settings") ?: JSONArray()
      val updatedSettingsCount = processSheetSettings(settingsArray)
      val docsArray = json.optJSONArray("importantDocuments") ?: JSONArray()
      val updatedDocsCount = processSheetImportantDocs(docsArray)

      val now = DateUtils.currentDhakaMillis()
      val msg = "Pulled from Google Sheets: $updatedFilesCount file(s), $updatedRmsCount RM(s), $updatedDocsCount document(s), $updatedSettingsCount setting(s) updated."
      database.appSettingDao().insertOrUpdateSyncStatus(
        currentStatus.copy(
          appsScriptUrl = activeWebAppUrl,
          lastSyncTimestamp = now,
          lastSyncStatus = "SUCCESS",
          lastSyncMessage = msg
        )
      )
      Result.success(msg)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  // Process Important Documents from Google Sheets
  private suspend fun processSheetImportantDocs(sheetDocsJson: JSONArray): Int {
    var updatedCount = 0
    val now = DateUtils.currentDhakaMillis()
    for (i in 0 until sheetDocsJson.length()) {
      val obj = sheetDocsJson.optJSONObject(i) ?: continue
      val docId = obj.optString("docId").trim()
      if (docId.isBlank()) continue
      val title = obj.optString("title", "Document $docId").trim()
      val category = obj.optString("category", "Policies & Circulars").trim()
      val description = obj.optString("description", "").trim()
      val fileName = obj.optString("fileName", "$title.pdf").trim()
      val fileType = obj.optString("fileType", "application/pdf").trim()
      val fileSize = obj.optLong("fileSize", 1024L)
      val uploadedBy = obj.optString("uploadedBy", "Admin").trim()
      val uploaderName = obj.optString("uploaderName", "Bank Operations").trim()
      val uploaderRole = obj.optString("uploaderRole", "ADMIN").trim()

      val existing = database.importantDocumentDao().getDocumentById(docId)
      if (existing != null) {
        if (existing.title != title || existing.category != category || existing.description != description) {
          database.importantDocumentDao().updateDocument(
            existing.copy(
              title = title,
              category = category,
              description = description,
              fileName = fileName,
              fileType = fileType,
              fileSizeBytes = fileSize,
              updatedAt = now,
              isSynced = true
            )
          )
          updatedCount++
        }
      } else {
        val newDoc = ImportantDocumentEntity(
          docId = docId,
          title = title,
          category = category,
          description = description,
          fileName = fileName,
          fileType = fileType,
          fileSizeBytes = fileSize,
          fileUri = "",
          storagePath = "",
          uploadedBy = uploadedBy,
          uploaderName = uploaderName,
          uploaderRole = uploaderRole,
          createdAt = now,
          updatedAt = now,
          isDeleted = false,
          isSynced = true
        )
        database.importantDocumentDao().insertDocument(newDoc)
        updatedCount++
      }
    }
    return updatedCount
  }

  private suspend fun processSheetRms(sheetRmsJson: JSONArray): Int {
    var updatedCount = 0
    val now = DateUtils.currentDhakaMillis()
    val sheetRmCodes = HashSet<String>()
    for (idx in 0 until sheetRmsJson.length()) {
      val o = sheetRmsJson.optJSONObject(idx) ?: continue
      val c = o.optString("rmCode").trim().uppercase()
      if (c.isNotBlank()) sheetRmCodes.add(c)
    }

    if (sheetRmsJson.length() > 0) {
      val allLocalUsers = database.userDao().getAllUsers()
      val protectedCodes = setOf("104393", "ADMIN0", "MENTOR0", "ADMIN", "MENTOR")
      for (u in allLocalUsers) {
        val uCode = u.rmCode.trim().uppercase()
        if (u.role == "RM" && uCode !in protectedCodes && uCode !in sheetRmCodes) {
          database.userDao().deleteUser(u.rmCode)
          database.rmTargetDao().deleteTargetForRm(u.rmCode)
          updatedCount++
        }
      }
    }

    val delRmsSetting = database.appSettingDao().getSetting("deleted_rm_codes")?.settingValue ?: ""
    val deletedCodes = delRmsSetting.split(",").map { it.trim().uppercase() }.filter { it.isNotBlank() }.toSet()

    for (i in 0 until sheetRmsJson.length()) {
      val obj = sheetRmsJson.optJSONObject(i) ?: continue
      val code = obj.optString("rmCode").trim().uppercase()
      if (code.isBlank() || code == "ADMIN0" || code == "MENTOR0" || code in deletedCodes) continue
      val name = obj.optString("name", "Relationship Manager").trim()
      val mobile = obj.optString("mobile", "").trim()
      val email = obj.optString("email", "").trim()
      val office = obj.optString("officeAddress", "Dhaka Branch").trim()
      val role = obj.optString("role", "RM").trim()
      val status = obj.optString("accountStatus", "ACTIVE").trim()
      val ccTarget = obj.optInt("creditCardTarget", 15)
      val corpTarget = obj.optInt("corporateCardTarget", 5)
      val b2bTarget = obj.optInt("b2bTarget", 2)
      val sheetHash = obj.optString("passwordHash", "").trim()
      val sheetSalt = obj.optString("salt", "").trim()

      val existing = database.userDao().getUser(code)
      if (existing != null) {
        val lastLocalPassChangeTime = database.appSettingDao().getSetting("RM_PASS_UPDATED_AT_$code")?.updatedAt ?: 0L
        val isRecentLocalPassChange = (now - lastLocalPassChangeTime) < 180_000L
        val hashToUse = if (isRecentLocalPassChange) existing.passwordHash else if (sheetHash.isNotBlank()) sheetHash else existing.passwordHash
        val saltToUse = if (isRecentLocalPassChange) existing.salt else if (sheetSalt.isNotBlank()) sheetSalt else existing.salt
        val hasChanges = existing.name != name || existing.mobile != mobile ||
                         existing.email != email || existing.officeAddress != office ||
                         existing.accountStatus != status ||
                         (!isRecentLocalPassChange && sheetHash.isNotBlank() && existing.passwordHash != sheetHash)
        if (hasChanges) {
          database.userDao().updateUser(
            existing.copy(
              name = name,
              mobile = mobile,
              email = email,
              officeAddress = office,
              accountStatus = status,
              passwordHash = hashToUse,
              salt = saltToUse
            )
          )
          updatedCount++
        }
      } else {
        val (finalHash, finalSalt) = if (sheetHash.isNotBlank() && sheetSalt.isNotBlank()) {
          Pair(sheetHash, sheetSalt)
        } else {
          val salt = SecurityUtils.generateSalt()
          Pair(SecurityUtils.hashPassword("#123456A", salt), salt)
        }
        val newUser = UserEntity(
          rmCode = code,
          name = name,
          role = role,
          passwordHash = finalHash,
          salt = finalSalt,
          mobile = mobile,
          email = email,
          officeAddress = office,
          accountStatus = status,
          mustChangePassword = false,
          createdAt = now,
          authUid = "AUTH_RM_$code"
        )
        database.userDao().insertUser(newUser)
        updatedCount++
      }

      val existingTarget = database.rmTargetDao().getTargetForRm(code)
      if (existingTarget == null) {
        database.rmTargetDao().insertOrUpdateTarget(
          RmTargetEntity(code, ccTarget, corpTarget, b2bTarget, now)
        )
      } else if (existingTarget.creditCardTarget != ccTarget ||
                 existingTarget.corporateCardTarget != corpTarget ||
                 existingTarget.b2bTarget != b2bTarget) {
        database.rmTargetDao().insertOrUpdateTarget(
          existingTarget.copy(
            creditCardTarget = ccTarget,
            corporateCardTarget = corpTarget,
            b2bTarget = b2bTarget,
            updatedAt = now
          )
        )
        updatedCount++
      }
    }
    return updatedCount
  }

  private suspend fun processSheetSettings(sheetSettingsJson: JSONArray): Int {
    var updatedCount = 0
    val now = DateUtils.currentDhakaMillis()
    for (i in 0 until sheetSettingsJson.length()) {
      val obj = sheetSettingsJson.optJSONObject(i) ?: continue
      val key = obj.optString("settingKey").trim()
      val value = obj.optString("settingValue").trim()
      if (key.isBlank()) continue
      val existing = database.appSettingDao().getSetting(key)
      if (existing == null || existing.settingValue != value) {
        database.appSettingDao().insertOrUpdateSetting(
          AppSettingEntity(
            settingKey = key,
            settingValue = value,
            updatedBy = "GoogleSheets_Universal",
            updatedAt = now
          )
        )
        updatedCount++
      }
    }
    return updatedCount
  }

  private suspend fun processSheetFiles(sheetFilesJson: JSONArray): Int {
    var updatedCount = 0
    val now = DateUtils.currentDhakaMillis()
    val sheetIds = HashSet<String>()
    for (idx in 0 until sheetFilesJson.length()) {
      val o = sheetFilesJson.optJSONObject(idx) ?: continue
      val fId = o.optString("fileId").trim().lowercase()
      val cc = o.optString("ccNumber").trim().lowercase()
      if (fId.isNotBlank()) sheetIds.add(fId)
      if (cc.isNotBlank()) sheetIds.add(cc)
    }

    if (sheetFilesJson.length() > 0) {
      val allLocalActive = database.customerFileDao().getAllActiveFiles()
      for (localFile in allLocalActive) {
        val fMatch = localFile.fileId.trim().lowercase() in sheetIds
        val ccMatch = localFile.ccNumber.isNotBlank() && localFile.ccNumber.trim().lowercase() in sheetIds
        if (!fMatch && !ccMatch && (localFile.isSynced || now - localFile.createdAt > 10000)) {
          database.customerFileDao().softDeleteFile(localFile.fileId, "GoogleSheets_Delete", now)
          database.customerFileDao().markFileSynced(localFile.fileId)
          updatedCount++
        }
      }
    }

    for (i in 0 until sheetFilesJson.length()) {
      val obj = sheetFilesJson.optJSONObject(i) ?: continue
      val fileId = obj.optString("fileId").trim()
      val ccNumber = obj.optString("ccNumber").trim()
      val targetId = if (fileId.isNotBlank()) fileId else ccNumber
      if (targetId.isBlank()) continue
      val existing = database.customerFileDao().getFileByAnyId(targetId)
        ?: (if (ccNumber.isNotBlank()) database.customerFileDao().getFileByAnyId(ccNumber) else null)
      if (existing != null && existing.isDeleted) continue

      val appStatus = obj.optString("applicationStatus", existing?.applicationStatus ?: "Submitted")
      val activeStatus = obj.optString("activeStatus", existing?.activeStatus ?: "Y")
      val remarks = obj.optString("remarks", existing?.remarks ?: "")
      val pendingDocs = obj.optString("pendingDocuments", existing?.pendingDocuments ?: "")
      val rmCode = obj.optString("assignedRmCode", existing?.assignedRmCode ?: "104393").trim().uppercase()
      val custName = obj.optString("customerName", existing?.customerName ?: "Customer")

      if (existing != null) {
        if (!existing.isSynced) continue
        val sheetUpdatedBy = obj.optString("updatedBy", "")
        val isManualSheetEdit = sheetUpdatedBy.equals("Manual_Sheet_Edit", ignoreCase = true)
        val wasRecentlyEditedLocally = (now - existing.updatedAt < 30_000) && (existing.updatedBy != "GoogleSheets_Sync")
        if (wasRecentlyEditedLocally && !isManualSheetEdit) continue

        val compName = obj.optString("companyName", existing.companyName)
        val offAddr = obj.optString("officeAddress", existing.officeAddress)
        val mob = obj.optString("mobile", existing.mobile)
        val em = obj.optString("email", existing.email)
        val prodType = obj.optString("productType", existing.productType)
        val cpvStatus = obj.optString("cpvStatus", existing.cpvStatus)
        val cpvRemarks = if (obj.has("cpvRemarks")) obj.optString("cpvRemarks", existing.cpvRemarks) else existing.cpvRemarks
        val remarksVal = if (obj.has("remarks")) obj.optString("remarks", existing.remarks) else existing.remarks
        val sheetSl = obj.optString("serialNumber", obj.optString("sl", existing.serialNumber))

        val hasStatusChanged = existing.applicationStatus != appStatus
        val hasActiveChanged = existing.activeStatus != activeStatus
        val hasRemarksChanged = existing.remarks != remarksVal || existing.cpvRemarks != cpvRemarks
        val hasDocsChanged = existing.pendingDocuments != pendingDocs
        val hasCcChanged = ccNumber.isNotBlank() && existing.ccNumber != ccNumber
        val hasRmChanged = rmCode.isNotBlank() && !existing.assignedRmCode.equals(rmCode, ignoreCase = true)
        val hasSlChanged = sheetSl.isNotBlank() && existing.serialNumber != sheetSl
        val hasDetailsChanged = existing.customerName != custName || existing.companyName != compName ||
                                existing.mobile != mob || existing.email != em || existing.officeAddress != offAddr ||
                                existing.productType != prodType || existing.cpvStatus != cpvStatus

        if (hasStatusChanged || hasActiveChanged || hasRemarksChanged || hasDocsChanged || hasCcChanged || hasRmChanged || hasDetailsChanged || hasSlChanged) {
          val updated = existing.copy(
            customerName = custName,
            companyName = compName,
            officeAddress = offAddr,
            mobile = mob,
            email = em,
            productType = prodType,
            applicationStatus = appStatus,
            activeStatus = activeStatus,
            remarks = remarksVal,
            cpvRemarks = cpvRemarks,
            cpvStatus = cpvStatus,
            pendingDocuments = pendingDocs,
            ccNumber = if (ccNumber.isNotBlank()) ccNumber else existing.ccNumber,
            assignedRmCode = if (rmCode.isNotBlank()) rmCode else existing.assignedRmCode,
            serialNumber = if (sheetSl.isNotBlank()) sheetSl else existing.serialNumber,
            updatedAt = now,
            updatedBy = "GoogleSheets_Sync",
            isSynced = true
          )
          database.customerFileDao().updateFile(updated)
          updatedCount++
        }
      } else {
        val sheetMobile = obj.optString("mobile", "").trim()
        val normSheetMobile = sheetMobile.replace(Regex("[^0-9]"), "").removePrefix("88").trimStart('0')
        if (normSheetMobile.length >= 6) {
          val isDuplicate = database.customerFileDao().getAllActiveFiles().any { f ->
            f.mobile.replace(Regex("[^0-9]"), "").removePrefix("88").trimStart('0') == normSheetMobile
          }
          if (isDuplicate) continue
        }
        val sheetSl = obj.optString("serialNumber", obj.optString("sl", ""))
        val newFile = CustomerFileEntity(
          fileId = targetId,
          customerName = custName,
          companyName = obj.optString("companyName", "N/A"),
          officeAddress = obj.optString("officeAddress", ""),
          mobile = sheetMobile,
          altMobile = "",
          email = obj.optString("email", ""),
          productType = obj.optString("productType", "Credit Card"),
          applicationStatus = appStatus,
          activeStatus = activeStatus,
          assignedRmCode = rmCode,
          ccNumber = ccNumber,
          pendingDocuments = pendingDocs,
          remarks = remarks,
          cpvStatus = obj.optString("cpvRemarks", "Pending"),
          serialNumber = sheetSl,
          createdAt = now,
          updatedAt = now,
          createdBy = "GoogleSheets",
          updatedBy = "GoogleSheets_Sync",
          isSynced = true
        )
        database.customerFileDao().insertFile(newFile)
        updatedCount++
      }
    }
    return updatedCount
  }

  fun generateCustomerFilesCsv(files: List<CustomerFileEntity>, isRmUser: Boolean): String {
    val sb = StringBuilder()
    val headers = if (isRmUser) {
      listOf(
        "SL", "CC-Number", "File ID", "Customer Name", "Company", "Mobile", "Email",
        "Product Type", "Status", "Active", "Pending Docs", "Remarks",
        "CPV Status", "Created Date", "Last Updated"
      )
    } else {
      listOf(
        "SL", "CC-Number", "File ID", "Customer Name", "Company", "Mobile", "Email",
        "Product Type", "Status", "Active", "RM Code", "Pending Docs", "Remarks",
        "CPV Status", "Created Date", "Last Updated"
      )
    }
    sb.append(headers.joinToString(",")).append("\n")
    for (f in files) {
      val ccDisplay = if (f.ccNumber.isNotBlank()) f.ccNumber else f.fileId
      val row = if (isRmUser) {
        listOf(
          escapeCsv(f.serialNumber),
          escapeCsv(ccDisplay),
          escapeCsv(f.fileId),
          escapeCsv(f.customerName),
          escapeCsv(f.companyName),
          escapeCsv(f.mobile),
          escapeCsv(f.email),
          escapeCsv(f.productType),
          escapeCsv(f.applicationStatus),
          escapeCsv(f.activeStatus),
          escapeCsv(f.pendingDocuments),
          escapeCsv(f.remarks),
          escapeCsv(f.cpvStatus),
          escapeCsv(DateUtils.formatDateTime(f.createdAt)),
          escapeCsv(DateUtils.formatDateTime(f.updatedAt))
        )
      } else {
        listOf(
          escapeCsv(f.serialNumber),
          escapeCsv(ccDisplay),
          escapeCsv(f.fileId),
          escapeCsv(f.customerName),
          escapeCsv(f.companyName),
          escapeCsv(f.mobile),
          escapeCsv(f.email),
          escapeCsv(f.productType),
          escapeCsv(f.applicationStatus),
          escapeCsv(f.activeStatus),
          escapeCsv(f.assignedRmCode),
          escapeCsv(f.pendingDocuments),
          escapeCsv(f.remarks),
          escapeCsv(f.cpvStatus),
          escapeCsv(DateUtils.formatDateTime(f.createdAt)),
          escapeCsv(DateUtils.formatDateTime(f.updatedAt))
        )
      }
      sb.append(row.joinToString(",")).append("\n")
    }
    return sb.toString()
  }

  fun generateRmMappingsCsv(rms: List<UserEntity>): String {
    val sb = StringBuilder()
    val headers = listOf("RM Code", "RM Name", "Mobile", "Email", "Office Branch", "Account Status", "Created Date", "Last Login")
    sb.append(headers.joinToString(",")).append("\n")
    for (r in rms) {
      val row = listOf(
        escapeCsv(r.rmCode),
        escapeCsv(r.name),
        escapeCsv(r.mobile),
        escapeCsv(r.email),
        escapeCsv(r.officeAddress),
        escapeCsv(r.accountStatus),
        escapeCsv(DateUtils.formatDateTime(r.createdAt)),
        escapeCsv(DateUtils.formatDateTime(r.lastLogin))
      )
      sb.append(row.joinToString(",")).append("\n")
    }
    return sb.toString()
  }

  private fun escapeCsv(value: String): String {
    val escaped = value.replace("\"", "\"\"")
    return if (escaped.contains(",") || escaped.contains("\n") || escaped.contains("\"")) {
      "\"$escaped\""
    } else {
      escaped
    }
  }

  // Location Tracking
  suspend fun updateUserLocation(
    rmCode: String,
    latitude: Double,
    longitude: Double,
    address: String,
    sourceAction: String,
    fileId: String? = null
  ) = withContext(Dispatchers.IO) {
    val now = DateUtils.currentDhakaMillis()
    database.userDao().updateUserLocation(rmCode, latitude, longitude, address, now)
    val user = database.userDao().getUser(rmCode)
    val userName = user?.name ?: rmCode
    database.userLocationLogDao().insertLocationLog(
      com.example.data.model.UserLocationLogEntity(
        rmCode = rmCode,
        userName = userName,
        latitude = latitude,
        longitude = longitude,
        address = address,
        sourceAction = sourceAction,
        relatedFileId = fileId,
        timestamp = now
      )
    )
  }

  fun getAllUsersFlow(): Flow<List<UserEntity>> {
    return database.userDao().getAllUsersFlow()
  }

  fun getRecentLocationLogsFlow(limit: Int = 100): Flow<List<com.example.data.model.UserLocationLogEntity>> {
    return database.userLocationLogDao().getRecentLocationLogsFlow(limit)
  }

  // 8. Communication: Messages, Team Events & Submissions
  fun getTeamHubMessagesFlow(): Flow<List<com.example.data.model.ChatMessageEntity>> {
    return database.communicationDao().getTeamHubMessagesFlow()
  }

  fun getVisibleMessagesFlow(myRmCode: String): Flow<List<com.example.data.model.ChatMessageEntity>> {
    return database.communicationDao().getVisibleMessagesFlow(myRmCode)
  }

  suspend fun sendChatMessage(
    messageText: String,
    recipientRmCode: String? = null,
    messageType: String = "TEXT",
    eventId: String? = null
  ): Result<com.example.data.model.ChatMessageEntity> = withContext(Dispatchers.IO) {
    val user = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val now = DateUtils.currentDhakaMillis()
    val msg = com.example.data.model.ChatMessageEntity(
      id = "MSG-${System.currentTimeMillis()}-${SecurityUtils.generateUniqueId().take(4)}",
      senderRmCode = user.rmCode,
      senderName = user.name,
      senderRole = user.role,
      recipientRmCode = recipientRmCode,
      messageText = messageText.trim(),
      timestamp = now,
      messageType = messageType,
      eventId = eventId
    )
    database.communicationDao().insertMessage(msg)
    Result.success(msg)
  }

  fun getAllTeamEventsFlow(): Flow<List<com.example.data.model.TeamEventEntity>> {
    return database.communicationDao().getAllEventsFlow()
  }

  suspend fun createTeamEvent(
    title: String,
    description: String,
    targetDate: String,
    allowedFieldsJson: String = "{\"allowCustomers\":true,\"allowFilesCount\":true,\"allowTargetDate\":true,\"allowLocation\":true,\"allowRemarks\":true}",
    customFieldsJson: String = ""
  ): Result<com.example.data.model.TeamEventEntity> = withContext(Dispatchers.IO) {
    val user = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val now = DateUtils.currentDhakaMillis()
    val eventId = "EVT-${System.currentTimeMillis()}"
    val event = com.example.data.model.TeamEventEntity(
      eventId = eventId,
      title = title.trim(),
      description = description.trim(),
      creatorRmCode = user.rmCode,
      creatorName = user.name,
      targetDate = targetDate.trim(),
      createdAt = now,
      status = "ACTIVE",
      allowedFieldsJson = allowedFieldsJson,
      customFieldsJson = customFieldsJson
    )
    database.communicationDao().insertEvent(event)

    val broadcastText = "NEW EVENT: '${event.title}'\nTarget Date: ${event.targetDate}\n${event.description}\n(Tap this event below to submit your customer details)"
    sendChatMessage(
      messageText = broadcastText,
      recipientRmCode = null,
      messageType = "EVENT",
      eventId = eventId
    )
    Result.success(event)
  }

  suspend fun submitEventResponse(
    eventId: String,
    filesCount: Int,
    requestedDate: String,
    location: String,
    remarks: String,
    customersJson: String = "[]"
  ): Result<Unit> = withContext(Dispatchers.IO) {
    val user = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized."))
    val now = DateUtils.currentDhakaMillis()
    val respId = "RESP-$eventId-${user.rmCode.trim().uppercase()}"
    val resp = com.example.data.model.EventResponseEntity(
      responseId = respId,
      eventId = eventId,
      rmCode = user.rmCode,
      rmName = user.name,
      filesCount = filesCount,
      requestedDate = requestedDate.trim(),
      location = location.trim(),
      remarks = remarks.trim(),
      customersJson = customersJson,
      submittedAt = now
    )
    database.communicationDao().insertOrUpdateResponse(resp)
    applicationScope.launch { triggerGoogleSheetsSync() }
    Result.success(Unit)
  }

  fun getResponsesForEventFlow(eventId: String): Flow<List<com.example.data.model.EventResponseEntity>> {
    return database.communicationDao().getResponsesForEventFlow(eventId)
  }

  suspend fun getResponsesForEvent(eventId: String): List<com.example.data.model.EventResponseEntity> = withContext(Dispatchers.IO) {
    database.communicationDao().getResponsesForEvent(eventId)
  }

  suspend fun getUserResponseForEvent(eventId: String, rmCode: String): com.example.data.model.EventResponseEntity? = withContext(Dispatchers.IO) {
    database.communicationDao().getUserResponseForEvent(eventId, rmCode)
  }

  // 9. Important Documents
  fun getAllImportantDocumentsFlow(): Flow<List<com.example.data.model.ImportantDocumentEntity>> {
    return database.importantDocumentDao().getAllDocumentsFlow()
  }

  suspend fun getAllImportantDocuments(): List<com.example.data.model.ImportantDocumentEntity> = withContext(Dispatchers.IO) {
    database.importantDocumentDao().getAllActiveDocuments()
  }

  suspend fun saveImportantDocument(
    docId: String?,
    title: String,
    category: String,
    description: String,
    fileName: String,
    fileType: String,
    fileSizeBytes: Long,
    fileUri: String,
    storagePath: String
  ): Result<com.example.data.model.ImportantDocumentEntity> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized operation."))

    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Access Denied: Only Admin or Mentor can upload or edit important documents."))
    }
    val now = DateUtils.currentDhakaMillis()
    val cleanId = docId?.trim().orEmpty()
    val existing = if (cleanId.isNotBlank()) database.importantDocumentDao().getDocumentById(cleanId) else null
    val targetDocId = existing?.docId ?: (if (cleanId.isNotBlank()) cleanId else "DOC_${System.currentTimeMillis()}")
    val entity = com.example.data.model.ImportantDocumentEntity(
      docId = targetDocId,
      title = title.trim(),
      category = category.trim().ifBlank { "Policies & Circulars" },
      description = description.trim(),
      fileName = fileName.trim().ifBlank { existing?.fileName ?: "Document_$targetDocId.pdf" },
      fileType = fileType.ifBlank { existing?.fileType ?: "application/pdf" },
      fileSizeBytes = if (fileSizeBytes > 0) fileSizeBytes else (existing?.fileSizeBytes ?: 1024L),
      fileUri = fileUri.ifBlank { existing?.fileUri ?: "" },
      storagePath = storagePath.ifBlank { existing?.storagePath ?: "" },
      uploadedBy = existing?.uploadedBy ?: currentUser.rmCode,
      uploaderName = existing?.uploaderName ?: currentUser.name,
      uploaderRole = existing?.uploaderRole ?: currentUser.role,
      createdAt = existing?.createdAt ?: now,
      updatedAt = now,
      isDeleted = false,
      isSynced = false
    )
    if (existing == null) {
      database.importantDocumentDao().insertDocument(entity)
    } else {
      database.importantDocumentDao().updateDocument(entity)
    }

    applicationScope.launch {
      try { triggerGoogleSheetsSync() } catch (_: Exception) {}
    }
    Result.success(entity)
  }

  suspend fun deleteImportantDocument(docId: String): Result<Unit> = withContext(Dispatchers.IO) {
    val currentUser = authRepository.currentUser.value
      ?: return@withContext Result.failure(Exception("Unauthorized operation."))

    if (currentUser.role != "ADMIN" && currentUser.role != "MENTOR") {
      return@withContext Result.failure(Exception("Access Denied: Only Admin or Mentor can delete important documents."))
    }
    val now = DateUtils.currentDhakaMillis()
    database.importantDocumentDao().softDeleteDocument(docId, now)

    // Store in deleted_doc_ids setting for sheet deletion
    val currentDel = database.appSettingDao().getSetting("deleted_doc_ids")?.settingValue ?: ""
    val updatedDel = if (currentDel.isBlank()) docId else "$currentDel,$docId"
    database.appSettingDao().insertOrUpdateSetting(
      AppSettingEntity("deleted_doc_ids", updatedDel, currentUser.rmCode, now)
    )

    applicationScope.launch {
      try { triggerGoogleSheetsSync() } catch (_: Exception) {}
    }
    Result.success(Unit)
  }
}
