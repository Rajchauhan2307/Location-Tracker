package com.raj.locationtracker;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class TrackingService extends Service {

    LocationManager lm;
    Handler handler = new Handler(Looper.getMainLooper());
    PowerManager.WakeLock wakeLock;
    long intervalMs = 5 * 60 * 1000;
    SharedPreferences prefs;
    double lastLat = 0, lastLon = 0;
    boolean hasLast = false;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);

        String channelId = "track_channel";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "Tracking", NotificationManager.IMPORTANCE_LOW);
            ch.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Location Tracker is running")
                .setContentText("Your location is being saved in the background")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
        startForeground(1, n);

        // Wakelock to keep CPU alive
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocTracker::wl");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int min = prefs.getInt("interval_min", 5);
        if (intent != null && intent.hasExtra("interval_min"))
            min = intent.getIntExtra("interval_min", 5);
        intervalMs = (long) min * 60 * 1000;

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        handler.removeCallbacks(repeater);
        saveLocationNow();
        handler.postDelayed(repeater, intervalMs);
        return START_STICKY;
    }

    Runnable repeater = new Runnable() {
        @Override
        public void run() {
            saveLocationNow();
            handler.postDelayed(this, intervalMs);
        }
    };

    void saveLocationNow() {
        try {
            Location loc = null;
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER))
                loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (loc != null) {
                String time = new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(new Date());
                String place = getPlaceName(loc.getLatitude(), loc.getLongitude());
                String entry = time + "  →  " + place + "  (" +
                        String.format(Locale.US, "%.4f, %.4f", loc.getLatitude(), loc.getLongitude()) + ")";
                File f = new File(getFilesDir(), "locations.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write(entry + "\n");
                fw.close();

                // KM calculation
                if (hasLast) {
                    float[] res = new float[1];
                    Location.distanceBetween(lastLat, lastLon, loc.getLatitude(), loc.getLongitude(), res);
                    float km = res[0] / 1000f;
                    String todayKey = "km_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    float total = prefs.getFloat(todayKey, 0f) + km;
                    prefs.edit().putFloat(todayKey, total).apply();
                }
                lastLat = loc.getLatitude(); lastLon = loc.getLongitude(); hasLast = true;
            }
        } catch (SecurityException se) {
        } catch (Exception e) {
        }
    }

    String getPlaceName(double lat, double lon) {
        try {
            Geocoder gc = new Geocoder(this, Locale.getDefault());
            List<Address> list = gc.getFromLocation(lat, lon, 1);
            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);
                String area = a.getSubLocality() != null ? a.getSubLocality() : a.getLocality();
                String city = a.getLocality() != null ? a.getLocality() : "";
                if (area == null) area = a.getFeatureName();
                return (area != null ? area : "") + (city.isEmpty() || city.equals(area) ? "" : ", " + city);
            }
        } catch (Exception e) {}
        return "Unknown location";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(repeater);
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // restart if user swipes app away (but keeps tracking on)
        if (prefs.getBoolean("is_tracking", false)) {
            Intent restart = new Intent(getApplicationContext(), TrackingService.class);
            restart.setPackage(getPackageName());
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart);
            else startService(restart);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
