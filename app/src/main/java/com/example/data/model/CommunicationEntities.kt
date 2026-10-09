package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "chat_messages",
  indices = [
    Index(value = ["timestamp"]),
    Index(value = ["recipientRmCode"])
  ]
)
data class ChatMessageEntity(
  @PrimaryKey
  val id: String,
  val senderRmCode: String,
  val senderName: String,
  val senderRole: String, // "ADMIN", "MENTOR", "RM"
  val recipientRmCode: String? = null, // null for General Team Hub broadcast, or specific RM code
  val messageText: String,
  val timestamp: Long,
  val messageType: String = "TEXT", // "TEXT", "EVENT", "CALL_LOG", "ANNOUNCEMENT"
  val eventId: String? = null
)

@Entity(
  tableName = "team_events",
  indices = [
    Index(value = ["createdAt"]),
    Index(value = ["status"])
  ]
)
data class TeamEventEntity(
  @PrimaryKey
  val eventId: String,
  val title: String, // Custom name given by creator (no hardcoded 'hand delivery'!)
  val description: String,
  val creatorRmCode: String,
  val creatorName: String,
  val targetDate: String, // e.g. "12/10/2026"
  val createdAt: Long,
  val status: String = "ACTIVE", // "ACTIVE", "COMPLETED"
  val allowedFieldsJson: String = "{\"allowCustomers\":true,\"allowFilesCount\":true,\"allowTargetDate\":true,\"allowLocation\":true,\"allowRemarks\":true}",
  val customFieldsJson: String = ""
)

@Entity(
  tableName = "event_responses",
  indices = [
    Index(value = ["eventId"]),
    Index(value = ["rmCode"])
  ]
)
data class EventResponseEntity(
  @PrimaryKey
  val responseId: String,
  val eventId: String,
  val rmCode: String,
  val rmName: String,
  val filesCount: Int,
  val requestedDate: String,
  val location: String,
  val remarks: String = "",
  val customersJson: String = "[]", // Stores multiple customer entries: [{"name":"...", "phone":"...", "ref":"..."}, ...]
  val submittedAt: Long
)

@Entity(
  tableName = "call_logs",
  indices = [
    Index(value = ["timestamp"]),
    Index(value = ["callerRmCode"]),
    Index(value = ["recipientRmCode"])
  ]
)
data class CallLogEntity(
  @PrimaryKey
  val callId: String,
  val callerRmCode: String,
  val callerName: String,
  val recipientRmCode: String,
  val recipientName: String,
  val recipientMobile: String,
  val callType: String = "INTERNET_VOICE", // "INTERNET_VOICE", "CELLULAR_PHONE", "GROUP_VOICE"
  val durationSeconds: Int = 0,
  val timestamp: Long,
  val status: String = "COMPLETED"
)
