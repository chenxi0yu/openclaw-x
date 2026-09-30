package ai.openclaw.app

import ai.openclaw.app.chat.ChatRunActivityPhase
import ai.openclaw.app.i18n.nativeString
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

private const val agentRunChannelId = "openclaw.agent.run"

/** Shared row with [NodeForegroundService]'s foreground notification so promotion never doubles. */
internal const val agentRunNotificationId = 1

/**
 * Builds the run live-update notification: promoted-ongoing ("Live Update") on
 * Android 16+ (status-bar chip), and the Xiaomi focus-notification template
 * renders it on the island. The classic indeterminate progress is what that
 * template reads (android.progress* extras) — do not swap it for ProgressStyle,
 * which writes a different extras shape the template does not recognize.
 * [textSuffix] carries voice/location capture badges when the foreground
 * service shows this row for other reasons.
 */
internal fun buildAgentRunNotification(
  context: Context,
  phase: ChatRunActivityPhase,
  textSuffix: String = "",
): Notification {
  val phaseLabel =
    when (phase) {
      ChatRunActivityPhase.Responding -> nativeString("Responding")
      ChatRunActivityPhase.Thinking -> nativeString("Thinking")
      ChatRunActivityPhase.Generating -> nativeString("Generating")
      ChatRunActivityPhase.Completed -> nativeString("Completed")
    }
  return NotificationCompat
    .Builder(context, agentRunChannelId)
    .setSmallIcon(R.mipmap.ic_launcher_monochrome)
    .setContentTitle(phaseLabel)
    .setContentText(nativeString("OpenClaw") + textSuffix)
    .setContentIntent(mainActivityPendingIntent(context, agentRunLaunchRequestCode))
    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
    .setOngoing(true)
    .setOnlyAlertOnce(true)
    .setShowWhen(false)
    .setProgress(0, 0, true)
    .setRequestPromotedOngoing(true)
    .apply {
      if (Build.VERSION.SDK_INT >= 36) {
        setShortCriticalText(phaseLabel)
      }
    }
    .build()
}

private const val agentRunLaunchRequestCode = 3

/**
 * Plain-post fallback for run state when the foreground service cannot hold the
 * row (service stopped, or background FGS promotion refused). The service
 * adopts the same notification id when it can promote.
 */
internal class AgentRunLiveUpdateNotifier(
  private val context: Context,
) {
  fun show(phase: ChatRunActivityPhase) {
    if (!canPostNotifications()) return
    ensureAgentRunChannel(context)
    notificationManager().notify(agentRunNotificationId, buildAgentRunNotification(context, phase))
  }

  fun cancel() {
    notificationManager().cancel(agentRunNotificationId)
  }

  private fun canPostNotifications(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return canPostConversationNotifications(Build.VERSION.SDK_INT) {
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    }
  }

  private fun notificationManager(): NotificationManager = context.getSystemService(NotificationManager::class.java)
}

internal fun ensureAgentRunChannel(context: Context) {
  val channel =
    NotificationChannel(
      agentRunChannelId,
      nativeString("Task status"),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      setShowBadge(false)
    }
  context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
