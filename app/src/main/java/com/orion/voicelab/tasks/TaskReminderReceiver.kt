package com.orion.voicelab.tasks

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.orion.voicelab.persistence.OrionDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TaskReminderReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.orion.voicelab.action.TRIGGER_TASK_REMINDER"
        const val ACTION_COMPLETE_TASK = "com.orion.voicelab.action.COMPLETE_TASK"
        const val ACTION_SNOOZE_TASK = "com.orion.voicelab.action.SNOOZE_TASK"
        const val ACTION_DISMISS_REMINDER = "com.orion.voicelab.action.DISMISS_TASK_REMINDER"
        const val ACTION_EXACT_ALARM_PERMISSION_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        const val EXTRA_TASK_ID = "extra_task_id"
        private const val DEFAULT_SNOOZE_MINUTES = 60L

        internal suspend fun handleIntentSuspend(
            context: Context,
            intent: Intent?,
            database: OrionDatabase = OrionDatabase.getInstance(context.applicationContext),
            scheduler: TaskReminderScheduler = AndroidTaskReminderScheduler(context.applicationContext)
        ) {
            if (intent == null) return
            val repo = TaskRepository(database.taskDao(), scheduler)
            val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
            val nm = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

            when (intent.action) {
                ACTION_TRIGGER_REMINDER -> {
                    if (taskId <= 0L) return
                    val task = repo.getTaskById(taskId) ?: return
                    if (task.status == com.orion.voicelab.data.TaskStatus.PENDING && task.reminderActive) {
                        AndroidTaskReminderScheduler.showReminderNotification(
                            context = context.applicationContext,
                            task = task,
                            isOverdue = false
                        )
                    } else {
                        scheduler.cancel(taskId)
                    }
                }

                ACTION_COMPLETE_TASK -> {
                    if (taskId <= 0L) return
                    repo.completeTask(taskId)
                    nm?.cancel(AndroidTaskReminderScheduler.notificationIdFor(taskId))
                }

                ACTION_SNOOZE_TASK -> {
                    if (taskId <= 0L) return
                    repo.snoozeReminder(taskId, DEFAULT_SNOOZE_MINUTES)
                    nm?.cancel(AndroidTaskReminderScheduler.notificationIdFor(taskId))
                }

                ACTION_DISMISS_REMINDER -> {
                    if (taskId <= 0L) return
                    repo.dismissReminder(taskId)
                    nm?.cancel(AndroidTaskReminderScheduler.notificationIdFor(taskId))
                }

                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                ACTION_EXACT_ALARM_PERMISSION_CHANGED -> {
                    repo.reconcileReminders()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                handleIntentSuspend(appContext, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
