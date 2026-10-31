package com.example.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.SessionManager
import com.example.ui.NotificationHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await

class TontineNotificationSyncJob : JobService() {

    private val jobScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onStartJob(params: JobParameters?): Boolean {
        jobScope.launch {
            try {
                val context = applicationContext
                val sessionManager = SessionManager.getInstance(context)
                val activeUserId = sessionManager.getActiveUserId()

                if (activeUserId != null) {
                    try {
                        FirebaseApp.initializeApp(context)
                    } catch (e: Exception) {
                        // Already initialized or ignore
                    }

                    val firestore = try {
                        val dbId = context.getString(R.string.firestore_database_id)
                        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), dbId)
                    } catch (e: Exception) {
                        try { FirebaseFirestore.getInstance() } catch (e2: Exception) { null }
                    }

                    if (firestore != null) {
                        val lastTimestamp = sessionManager.getLastNotifiedTimestamp()
                        val snapshot = firestore.collection("notifications")
                            .whereEqualTo("userId", activeUserId)
                            .get()
                            .await()

                        var maxTimestamp = lastTimestamp
                        val allDocs = snapshot.documents.toMutableList()

                        try {
                            val broadcastSnapshot = firestore.collection("notifications")
                                .whereEqualTo("userId", "ALL")
                                .get()
                                .await()
                            allDocs.addAll(broadcastSnapshot.documents)
                        } catch (e: Exception) {
                            Log.w("NotificationSyncJob", "Broadcast fetch ignored", e)
                        }

                        for (doc in allDocs) {
                            val isRead = doc.getBoolean("isRead") ?: false
                            val timestamp = doc.getLong("timestamp") ?: 0L
                            val title = doc.getString("title") ?: "TontinePay"
                            val message = doc.getString("message") ?: ""
                            val tontineId = doc.getString("tontineId")

                            if (!isRead && timestamp > lastTimestamp && !sessionManager.isNotificationDispatched(doc.id)) {
                                NotificationHelper.showSystemNotification(
                                    context = context,
                                    notificationId = doc.id.hashCode(),
                                    title = title,
                                    message = message,
                                    tontineId = tontineId,
                                    notificationDocId = doc.id
                                )
                                sessionManager.markNotificationDispatched(doc.id)
                                if (timestamp > maxTimestamp) {
                                    maxTimestamp = timestamp
                                }
                            }
                        }
                        if (maxTimestamp > lastTimestamp) {
                            sessionManager.setLastNotifiedTimestamp(maxTimestamp)
                        }

                        // Also check for app updates periodically in background
                        try {
                            val updateDoc = firestore.collection("system").document("app_update").get().await()
                            if (updateDoc.exists()) {
                                val latestVer = updateDoc.getString("latestVersion") ?: ""
                                val currentVer = sessionManager.getAppliedVersion()
                                if (latestVer.isNotBlank() && isNewerVersion(latestVer, currentVer)) {
                                    val notifKey = "update_job_$latestVer"
                                    if (!sessionManager.isNotificationDispatched(notifKey)) {
                                        sessionManager.markNotificationDispatched(notifKey)
                                        NotificationHelper.showSystemNotification(
                                            context = context,
                                            notificationId = 99991,
                                            title = "🚀 Mise à jour $latestVer disponible",
                                            message = "Une nouvelle version de TontinePay ($latestVer) est disponible. Appuyez pour mettre à jour.",
                                            tontineId = null,
                                            notificationDocId = notifKey
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("NotificationSyncJob", "Could not check app_update in background", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("NotificationSyncJob", "Error checking background notifications", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        jobScope.cancel()
        return true
    }

    private fun isNewerVersion(serverVer: String, currentVer: String): Boolean {
        val cleanServer = serverVer.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        val cleanCurrent = currentVer.removePrefix("v").split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(cleanServer.size, cleanCurrent.size)) {
            val s = cleanServer.getOrElse(i) { 0 }
            val c = cleanCurrent.getOrElse(i) { 0 }
            if (s > c) return true
            if (s < c) return false
        }
        return false
    }

    companion object {
        private const val JOB_ID = 8842

        fun schedule(context: Context) {
            try {
                val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
                val component = ComponentName(context, TontineNotificationSyncJob::class.java)
                val builder = JobInfo.Builder(JOB_ID, component)
                    .setPeriodic(15 * 60 * 1000L) // Every 15 minutes
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)

                scheduler.schedule(builder.build())
            } catch (e: Exception) {
                Log.e("NotificationSyncJob", "Failed to schedule job", e)
            }
        }
    }
}
