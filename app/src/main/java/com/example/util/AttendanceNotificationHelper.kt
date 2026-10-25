package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.R

object AttendanceNotificationHelper {

    private const val CHANNEL_ID = "attendance_channel"
    private const val CHANNEL_NAME = "Notifikasi Absensi Santri"
    private const val CHANNEL_DESC = "Pemberitahuan hasil absensi berhasil atau gagal"
    private const val NOTIFICATION_ID_SUCCESS = 1001
    private const val NOTIFICATION_ID_FAILURE = 1002
    private var isChannelCreated = false
    private var currentToast: Toast? = null

    private fun createNotificationChannel(context: Context) {
        if (isChannelCreated) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 150, 100, 150)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
        isChannelCreated = true
    }

    fun showSuccess(
        context: Context,
        studentName: String,
        studentId: String,
        sessionName: String,
        status: String
    ) {
        // 1. Toast interaktif instan (batalkan toast sebelumnya jika ada agar tidak double/antre)
        val message = "✅ ABSENSI BERHASIL: $studentName ($studentId)\nSesi: $sessionName • Status: $status"
        currentToast?.cancel()
        currentToast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).also {
            it.show()
        }

        // 2. Getar haptic pendek
        vibrate(context, isSuccess = true)

        // 3. System notification bar (gunakan ID tetap agar menggantikan notifikasi sebelumnya)
        try {
            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Absensi Berhasil: $studentName")
                .setContentText("$sessionName • Status: $status (ID: $studentId)")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("Santri $studentName ($studentId) berhasil diabsen untuk sesi $sessionName dengan status $status.")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
                .build()

            notificationManager.notify(NOTIFICATION_ID_SUCCESS, notification)
        } catch (_: Exception) {
            // Ignored if notification permission denied on Android 13+
        }
    }

    fun showFailure(
        context: Context,
        reason: String,
        studentName: String? = null
    ) {
        // 1. Toast interaktif instan (batalkan toast sebelumnya agar tidak double/antre)
        val message = if (studentName != null) {
            "❌ ABSENSI GAGAL: $studentName\n$reason"
        } else {
            "❌ ABSENSI GAGAL: $reason"
        }
        currentToast?.cancel()
        currentToast = Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).also {
            it.show()
        }

        // 2. Getar haptic peringatan (double pulse)
        vibrate(context, isSuccess = false)

        // 3. System notification bar (gunakan ID tetap agar menggantikan notifikasi sebelumnya)
        try {
            createNotificationChannel(context)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val title = if (studentName != null) "Absensi Gagal: $studentName" else "Absensi Gagal"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(reason)
                .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                .build()

            notificationManager.notify(NOTIFICATION_ID_FAILURE, notification)
        } catch (_: Exception) {
            // Ignored if notification permission denied on Android 13+
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context, isSuccess: Boolean) {
        try {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isSuccess) {
                    vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 100, 80, 150), -1)
                    )
                }
            } else {
                if (isSuccess) {
                    vibrator.vibrate(80)
                } else {
                    vibrator.vibrate(longArrayOf(0, 100, 80, 150), -1)
                }
            }
        } catch (_: Exception) {}
    }
}
