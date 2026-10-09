package com.example.util

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.util.Log

object CallingService {
  private const val TAG = "CallingService"
  private var toneGenerator: ToneGenerator? = null

  fun playDialTone() {
    try {
      if (toneGenerator == null) {
        toneGenerator = ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80)
      }
      toneGenerator?.startTone(ToneGenerator.TONE_SUP_RINGTONE, 3000)
    } catch (e: Exception) {
      Log.w(TAG, "Audio tone unavailable: ${e.message}")
    }
  }

  fun playConnectedTone() {
    try {
      toneGenerator?.stopTone()
      toneGenerator?.startTone(ToneGenerator.TONE_PROP_PROMPT, 300)
    } catch (_: Exception) {}
  }

  fun playEndCallTone() {
    try {
      toneGenerator?.stopTone()
      toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 500)
    } catch (_: Exception) {}
  }

  fun stopTone() {
    try {
      toneGenerator?.stopTone()
    } catch (_: Exception) {}
  }

  fun release() {
    try {
      toneGenerator?.release()
      toneGenerator = null
    } catch (_: Exception) {}
  }

  /**
   * Directly launches the device dialer with the phone number.
   * This is guaranteed to work on all physical phones without telephony permission restriction.
   */
  fun dialPhoneNumber(context: Context, phoneNumber: String): Boolean {
    return try {
      val cleanNumber = phoneNumber.trim().replace(" ", "").replace("-", "")
      val dialUri = Uri.parse("tel:$cleanNumber")
      val intent = Intent(Intent.ACTION_DIAL, dialUri).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
      }
      context.startActivity(intent)
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch phone dialer: ${e.message}")
      false
    }
  }
}
