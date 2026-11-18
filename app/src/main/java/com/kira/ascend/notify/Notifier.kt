package com.kira.ascend.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kira.ascend.MainActivity
import com.kira.ascend.R
import java.util.Calendar

object Notifier {
    private const val CHANNEL = "ascend_main"
    const val REQ_DAILY = 1001
    const val REQ_TIMER = 1002

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ascend System", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, id: Int, title: String, text: String) {
        ensureChannel(ctx)
        val nmc = NotificationManagerCompat.from(ctx)
        if (!nmc.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            nmc.notify(id, n)
        } catch (e: SecurityException) {
            // Permission revoked; ignore.
        }
    }

    private fun pending(ctx: Context, req: Int, id: Int, title: String, text: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .putExtra("id", id).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(
            ctx, req, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun scheduleOnce(ctx: Context, req: Int, atMs: Long, title: String, text: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, pending(ctx, req, req, title, text))
    }

    fun cancel(ctx: Context, req: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(ctx, req, req, "", ""))
    }

    fun applyDaily(ctx: Context, enabled: Boolean, hour: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, REQ_DAILY, REQ_DAILY, "Ascend", "Daily quests are waiting.")
        am.cancel(pi)
        if (!enabled) return
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 1)
        val title = intent.getStringExtra("title") ?: "Ascend"
        val text = intent.getStringExtra("text") ?: ""
        if (text.isNotEmpty()) Notifier.show(context, id, title, text)
    }
}
