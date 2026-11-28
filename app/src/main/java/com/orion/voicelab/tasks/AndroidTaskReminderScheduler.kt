package com.orion.voicelab.tasks

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.orion.voicelab.MainActivity
import com.orion.voicelab.R
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskStatus

class AndroidTaskReminderScheduler(
    context: Context,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : TaskReminderScheduler {

    private val appContext = context.applicationContext
    private val alarmManager: AlarmManager? =
        appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationManager: NotificationManager? =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    companion object {
        const val CHANNEL_ID = "orion_task_reminders"
        const val EXTRA_OPEN_TAB = "orion_open_tab"
        const val EXTRA_TASK_ID = "orion_task_id"
        const val TAB_TASKS_VALUE = "TASKS"
        private const val NOTIFICATION_BASE_ID = 40_000

        fun notificationIdFor(taskId: Long): Int =
            NOTIFICATION_BASE_ID + (taskId.coerceAtLeast(0L) % 100_000L).toInt()

        fun canScheduleExactAlarms(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            return manager.canScheduleExactAlarms()
        }

        /** Returns a system-settings intent; callers must launch it only after an explicit user action. */
        fun exactAlarmSettingsIntent(context: Context): Intent? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms(context)) return null
            return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }

        fun hasNotificationPermission(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

        fun ensureNotificationChannel(context: Context) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Recordatorios de ORION",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Avisos locales de tareas, compromisos y seguimientos de ORION."
                    enableVibration(true)
                }
                nm.createNotificationChannel(channel)
            }
        }

        fun buildAlarmPendingIntent(
            context: Context,
            taskId: Long,
            flags: Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ): PendingIntent {
            val intent = Intent(context.applicationContext, TaskReminderReceiver::class.java).apply {
                action = TaskReminderReceiver.ACTION_TRIGGER_REMINDER
                data = Uri.parse("orion://task/$taskId")
                putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId)
            }
            val reqCode = (taskId.coerceAtLeast(1L) % Int.MAX_VALUE).toInt()
            return PendingIntent.getBroadcast(context.applicationContext, reqCode, intent, flags)
        }

        fun showReminderNotification(
            context: Context,
            task: TaskItem,
            isOverdue: Boolean = false
        ) {
            if (!hasNotificationPermission(context)) return
            ensureNotificationChannel(context)
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            // Reconciliation may run on every app start, reboot or time-zone change.
            // Do not re-alert for an overdue reminder that is already visible.
            val notificationId = notificationIdFor(task.id)
            if (nm.activeNotifications.any { it.id == notificationId }) return

            val openIntent = Intent(context.applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_TAB, TAB_TASKS_VALUE)
                putExtra(EXTRA_TASK_ID, task.id)
            }
            val openPendingIntent = PendingIntent.getActivity(
                context.applicationContext,
                notificationIdFor(task.id),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val completeIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                notificationIdFor(task.id) + 100_000,
                Intent(context.applicationContext, TaskReminderReceiver::class.java).apply {
                    action = TaskReminderReceiver.ACTION_COMPLETE_TASK
                    data = Uri.parse("orion://task/${task.id}/complete")
                    putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val snoozeIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                notificationIdFor(task.id) + 200_000,
                Intent(context.applicationContext, TaskReminderReceiver::class.java).apply {
                    action = TaskReminderReceiver.ACTION_SNOOZE_TASK
                    data = Uri.parse("orion://task/${task.id}/snooze")
                    putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val dismissIntent = PendingIntent.getBroadcast(
                context.applicationContext,
                notificationIdFor(task.id) + 300_000,
                Intent(context.applicationContext, TaskReminderReceiver::class.java).apply {
                    action = TaskReminderReceiver.ACTION_DISMISS_REMINDER
                    data = Uri.parse("orion://task/${task.id}/dismiss")
                    putExtra(TaskReminderReceiver.EXTRA_TASK_ID, task.id)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val titlePrefix = if (isOverdue) "Recordatorio atrasado" else "Recordatorio de ORION"
            val subtitleParts = buildList {
                if (task.priority == TaskPriority.URGENT || task.priority == TaskPriority.HIGH) {
                    add("Prioridad ${task.priority.label.lowercase()}")
                }
                if (!task.contactName.isNullOrBlank()) {
                    add("Contacto: ${task.contactName}")
                }
                if (task.description.isNotBlank()) {
                    add(task.description)
                }
            }
            val bodyText = if (subtitleParts.isEmpty()) {
                task.title
            } else {
                "${task.title}\n${subtitleParts.joinToString(" · ")}"
            }

            val notification = NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titlePrefix)
                .setContentText(task.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                // Keep task/contact details private on lock screens.
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(
                    NotificationCompat.Builder(context.applicationContext, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("Recordatorio de ORION")
                        .setContentText("Tienes una tarea pendiente")
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .build()
                )
                .setAutoCancel(true)
                .setContentIntent(openPendingIntent)
                .addAction(0, "Completar", completeIntent)
                .addAction(0, "Posponer 1 h", snoozeIntent)
                .addAction(0, "Descartar", dismissIntent)
                .build()

            nm.notify(notificationIdFor(task.id), notification)
        }
    }

    init {
        ensureNotificationChannel(appContext)
    }

    override fun schedule(task: TaskItem) {
        cancelAlarmOnly(task.id)
        if (task.status != TaskStatus.PENDING || !task.reminderActive || task.reminderAtMs == null) {
            notificationManager?.cancel(notificationIdFor(task.id))
            return
        }
        val now = clockMs()
        if (task.reminderAtMs <= now) {
            showReminderNotification(appContext, task, isOverdue = true)
            return
        }
        scheduleAlarm(task.id, task.reminderAtMs)
    }

    override fun cancel(taskId: Long) {
        cancelAlarmOnly(taskId)
        notificationManager?.cancel(notificationIdFor(taskId))
    }

    override fun reconcileAll(tasks: List<TaskItem>, nowMs: Long) {
        for (task in tasks) {
            if (task.status != TaskStatus.PENDING || !task.reminderActive || task.reminderAtMs == null) {
                cancel(task.id)
            } else if (task.reminderAtMs <= nowMs) {
                cancelAlarmOnly(task.id)
                showReminderNotification(appContext, task, isOverdue = true)
            } else {
                cancelAlarmOnly(task.id)
                scheduleAlarm(task.id, task.reminderAtMs)
            }
        }
    }

    private fun scheduleAlarm(taskId: Long, triggerAtMs: Long) {
        val manager = alarmManager ?: return
        val pendingIntent = buildAlarmPendingIntent(appContext, taskId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canScheduleExactAlarms(appContext)) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
                return
            } catch (_: SecurityException) {
                // Permission can change between the capability check and the scheduling call.
            }
        }
        // Android may defer this alarm; keep reminders functional without special access.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pendingIntent)
    }

    private fun cancelAlarmOnly(taskId: Long) {
        val existing = buildAlarmPendingIntent(
            context = appContext,
            taskId = taskId,
            flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
            alarmManager?.cancel(existing)
            existing.cancel()
        }
    }
}
