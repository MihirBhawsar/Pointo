package com.health.pointo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class LocationService : Service(), LocationListener {
    private val TAG: String = "MainActivity"

    private lateinit var locationManager: LocationManager
    private val channelId = "location_tracker_channel"
    private val notificationId = 1

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
         super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand")
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, " >= Build.VERSION_CODES.Q")
            startForeground(
                notificationId,
                createNotification("Waiting for location..."),
                FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            Log.d(TAG, " >= Build.VERSION_CODES.Q else")
            startForeground(notificationId, createNotification("Waiting for location..."))

        }
        requestLocationUpdates()
        return START_STICKY
    }

    private fun createNotification(content: String): Notification {
        Log.d(TAG, "createNotification")
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracker")
            .setContentText(content)
            .setSmallIcon(R.drawable.location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createNotificationChannel() {
        Log.d(TAG, "createNotificationChannel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Location Tracker Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun requestLocationUpdates() {
        Log.d(TAG, "requestLocationUpdates")
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000,
                10f,
                this
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    override fun onLocationChanged(location: Location) {
        Log.d(TAG, "onLocationChanged")
        val content = "Lat: ${location.latitude}, Lon: ${location.longitude}"
        val notification = createNotification(content)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        locationManager.removeUpdates(this)
        stopForeground(true)
    }
}
