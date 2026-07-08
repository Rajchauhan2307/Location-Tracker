package com.raj.locationtracker;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.*;
import android.os.*;
import android.telephony.SmsManager;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class TrackingService extends Service implements SensorEventListener {

    LocationManager lm;
    Handler handler = new Handler(Looper.getMainLooper());
    PowerManager.WakeLock wakeLock;
    long intervalMs = 5 * 60 * 1000;
    SharedPreferences prefs;
    double lastLat = 0, lastLon = 0;
    long lastTime = 0;
    boolean hasLast = false;

    // Step sensor
    SensorManager sensorManager;
    Sensor stepSensor;
    float stepBaseline = -1;

    // SMS timing
    long lastSmsMs = 0;

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

        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocTracker::wl");
            wakeLock.acquire();
        } catch (Exception e) {}

        // Step sensor setup
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            if (sensorManager != null) {
                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (stepSensor != null) {
                    sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        } catch (Exception e) {}
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
                long nowT = System.currentTimeMillis();
                String time = new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US).format(new Date(nowT));
                String place = getPlaceName(loc.getLatitude(), loc.getLongitude());

                // Speed / mode detection
                String mode = "Idle";
                double kmh = 0;
                if (hasLast) {
                    float[] res = new float[1];
                    Location.distanceBetween(lastLat, lastLon, loc.getLatitude(), loc.getLongitude(), res);
                    double meters = res[0];
                    double km = meters / 1000.0;
                    double hours = (nowT - lastTime) / 3600000.0;
                    if (hours > 0) kmh = km / hours;

                    if (kmh < 2) mode = "Idle";
                    else if (kmh < 7) mode = "Walk";
                    else if (kmh < 20) mode = "Cycle";
                    else mode = "Vehicle";

                    // KM total (only if actually moving)
                    if (kmh >= 2) {
                        String todayKey = "km_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                        float total = prefs.getFloat(todayKey, 0f) + (float) km;
                        prefs.edit().putFloat(todayKey, total).apply();

                        // Vehicle km (separate)
                        if (mode.equals("Vehicle")) {
                            String vKey = "vkm_" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                            float vtotal = prefs.getFloat(vKey, 0f) + (float) km;
                            prefs.edit().putFloat(vKey, vtotal).apply();
                        }
                    }
                }

                String entry = time + "  →  " + place + "  (" +
                        String.format(Locale.US, "%.4f, %.4f", loc.getLatitude(), loc.getLongitude()) + ")";
                File f = new File(getFilesDir(), "locations.txt");
                FileWriter fw = new FileWriter(f, true);
                fw.write(entry + "\n");
                fw.close();

                lastLat = loc.getLatitude(); lastLon = loc.getLongitude();
                lastTime = nowT; hasLast = true;

                // Auto SMS
                trySendSms(loc.getLatitude(), loc.getLongitude(), place);
            }
        } catch (SecurityException se) {
        } catch (Exception e) {
        }
    }

    // ---------- AUTO SMS ----------
    void trySendSms(double lat, double lon, String place) {
        try {
            boolean smsOn = prefs.getBoolean("sms_on", false);
            if (!smsOn) return;
            int smsMin = prefs.getInt("sms_interval_min", 30);
            long smsIntervalMs = (long) smsMin * 60 * 1000;
            long now = System.currentTimeMillis();
            long last = prefs.getLong("last_sms_ms", 0);
            if (now - last < smsIntervalMs) return;

            String numbersRaw = prefs.getString("sms_numbers", "");
            if (numbersRaw.trim().isEmpty()) return;
            String[] numbers = numbersRaw.split(",");

            String mapLink = "https://maps.google.com/?q=" + lat + "," + lon;
            String time = new SimpleDateFormat("dd MMM, hh:mm a", Locale.US).format(new Date(now));
            String msg = "My location (" + time + "): " + place + "\n" + mapLink;

            SmsManager sms = SmsManager.getDefault();
            for (String num : numbers) {
                String n = num.trim();
                if (n.isEmpty()) continue;
                if (!n.startsWith("+") && n.length() == 10) n = "+91" + n; // India default
                try {
                    ArrayList<String> parts = sms.divideMessage(msg);
                    sms.sendMultipartTextMessage(n, null, parts, null, null);
                } catch (Exception e) {}
            }
            prefs.edit().putLong("last_sms_ms", now).apply();
        } catch (Exception e) {}
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

    // ---------- STEP SENSOR ----------
    @Override
    public void onSensorChanged(SensorEvent event) {
        try {
            if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
                float total = event.values[0];
                if (stepBaseline < 0) {
                    // restore baseline for today if exists
                    String bKey = "step_base_" + today();
                    float saved = prefs.getFloat(bKey, -1);
                    if (saved < 0) {
                        stepBaseline = total;
                        prefs.edit().putFloat(bKey, total).apply();
                    } else {
                        stepBaseline = saved;
                    }
                }
                float todaySteps = total - stepBaseline;
                if (todaySteps < 0) todaySteps = 0;
                prefs.edit().putInt("steps_" + today(), (int) todaySteps).apply();
            }
        } catch (Exception e) {}
    }

    String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(repeater);
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception e) {}
        try { if (sensorManager != null) sensorManager.unregisterListener(this); } catch (Exception e) {}
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
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
