package com.example.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat

object SmsService {
  private const val TAG = "SmsService"

  /**
   * Dispatches an SMS to the given recipient mobile number using Android's SmsManager.
   * Returns true if successfully queued/sent to carrier, false if telephony/permission failed.
   */
  fun sendSms(
    context: Context,
    recipientMobile: String,
    messageText: String
  ): Boolean {
    val cleanMobile = recipientMobile.trim().replace(" ", "").replace("-", "")
    if (cleanMobile.isBlank() || messageText.isBlank()) {
      Log.w(TAG, "Cannot send SMS: empty recipient or message")
      return false
    }

    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      android.Manifest.permission.SEND_SMS
    ) == PackageManager.PERMISSION_GRANTED

    return try {
      val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(SmsManager::class.java)
          ?: @Suppress("DEPRECATION") SmsManager.getDefault()
      } else {
        @Suppress("DEPRECATION")
        SmsManager.getDefault()
      }
      val parts = smsManager.divideMessage(messageText)
      if (parts.size > 1) {
        smsManager.sendMultipartTextMessage(cleanMobile, null, parts, null, null)
      } else {
        smsManager.sendTextMessage(cleanMobile, null, messageText, null, null)
      }
      Log.i(TAG, "SMS successfully dispatched to $cleanMobile")
      true
    } catch (se: SecurityException) {
      Log.w(TAG, "SEND_SMS permission not granted or restricted by OS: ${se.message}")
      false
    } catch (e: Exception) {
      Log.w(TAG, "Failed to send hardware SMS to $cleanMobile: ${e.message}")
      false
    }
  }

  /**
   * Fallback to opening system SMS/messaging app with recipient number and prefilled text.
   */
  fun launchSmsApp(context: Context, recipientMobile: String, messageText: String) {
    try {
      val cleanMobile = recipientMobile.trim().replace(" ", "").replace("-", "")
      val uri = Uri.parse("smsto:$cleanMobile")
      val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra("sms_body", messageText)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
    } catch (e: Exception) {
      Log.w(TAG, "Failed to launch SMS app intent: ${e.message}")
    }
  }
}
