package com.raj.locationtracker;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.location.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class TrackingService extends Service {

    LocationManager lm;
    LocationListener listener;
    Handler handler = new Handler(Looper.getMainLooper());
    static final long INTERVAL = 10 * 60 * 1000; // 10 minutes

    @Override
    public void onCreate() {
        super.onCreate();
        String channelId = "track_channel";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(channelId, "Tracking", NotificationManager.IMPORTANCE_LOW);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        }
        Notification n = new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Location Tracker chalu hai")
                .setContentText("Aapki location background mein save ho rahi hai")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setOngoing(true)
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        saveLocationNow();
        handler.postDelayed(repeater, INTERVAL);
        return START_STICKY;
    }

    Runnable repeater = new Runnable() {
        @Override
        public void run() {
            saveLocationNow();
            handler.postDelayed(this, INTERVAL);
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
            }
        } catch (SecurityException se) {
            // permission missing
        } catch (Exception e) {
            // ignore
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
        return "Unknown jagah";
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(repeater);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
              }
