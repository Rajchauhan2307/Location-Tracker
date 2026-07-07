package com.raj.locationtracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import android.view.*;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {

    TextView statusText;
    LinearLayout listLayout;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 60, 40, 40);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("📍 Location Tracker");
        title.setTextSize(26);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        statusText = new TextView(this);
        statusText.setText("Status: Band hai");
        statusText.setTextSize(16);
        statusText.setPadding(0, 0, 0, 30);
        root.addView(statusText);

        Button startBtn = new Button(this);
        startBtn.setText("▶ Tracking Chalu Karo");
        startBtn.setOnClickListener(v -> {
            if (checkPerms()) {
                startService(new Intent(this, TrackingService.class));
                statusText.setText("Status: CHALU hai ✅");
                Toast.makeText(this, "Tracking shuru!", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(startBtn);

        Button stopBtn = new Button(this);
        stopBtn.setText("⏹ Tracking Band Karo");
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, TrackingService.class));
            statusText.setText("Status: Band hai");
            Toast.makeText(this, "Tracking band!", Toast.LENGTH_SHORT).show();
        });
        root.addView(stopBtn);

        Button viewBtn = new Button(this);
        viewBtn.setText("📖 Aaj Ka Timeline Dekho");
        viewBtn.setOnClickListener(v -> showTimeline());
        root.addView(viewBtn);

        Button shareTodayBtn = new Button(this);
        shareTodayBtn.setText("📤 Aaj Ka Data Share Karo");
        shareTodayBtn.setOnClickListener(v -> shareData(true));
        root.addView(shareTodayBtn);

        Button shareAllBtn = new Button(this);
        shareAllBtn.setText("📤 Saara Data Share Karo");
        shareAllBtn.setOnClickListener(v -> shareData(false));
        root.addView(shareAllBtn);

        TextView listTitle = new TextView(this);
        listTitle.setText("\n--- Timeline ---");
        listTitle.setTextSize(18);
        listTitle.setPadding(0, 30, 0, 10);
        root.addView(listTitle);

        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listLayout);

        setContentView(scroll);
        showTimeline();
    }

    boolean checkPerms() {
        List<String> need = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (Build.VERSION.SDK_INT >= 29 && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        if (Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (!need.isEmpty()) {
            ActivityCompat.requestPermissions(this, need.toArray(new String[0]), 1);
            Toast.makeText(this, "Permission do, phir dobara Start dabao", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    void shareData(boolean todayOnly) {
        try {
            File f = new File(getFilesDir(), "locations.txt");
            if (!f.exists()) {
                Toast.makeText(this, "Abhi koi data nahi hai", Toast.LENGTH_SHORT).show();
                return;
            }
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            StringBuilder sb = new StringBuilder();
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (todayOnly)
                sb.append("📍 Mera Aaj Ka Timeline (").append(today).append(")\n\n");
            else
                sb.append("📍 Mera Poora Location Timeline\n\n");
            int count = 0;
            while ((line = br.readLine()) != null) {
                if (!todayOnly || line.startsWith(today)) {
                    sb.append("🕒 ").append(line).append("\n");
                    count++;
                }
            }
            br.close();
            if (count == 0) {
                Toast.makeText(this, "Is din ka koi record nahi", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT, sb.toString());
            startActivity(Intent.createChooser(share, "Timeline share karo"));
        } catch (Exception e) {
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    void showTimeline() {
        listLayout.removeAllViews();
        try {
            File f = new File(getFilesDir(), "locations.txt");
            if (!f.exists()) {
                TextView t = new TextView(this);
                t.setText("Abhi koi record nahi. Tracking chalu karo.");
                listLayout.addView(t);
                return;
            }
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            List<String> lines = new ArrayList<>();
            while ((line = br.readLine()) != null) lines.add(line);
            br.close();
            Collections.reverse(lines);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            for (String l : lines) {
                if (l.startsWith(today)) {
                    TextView t = new TextView(this);
                    t.setText("🕒 " + l);
                    t.setTextSize(14);
                    t.setPadding(0, 12, 0, 12);
                    listLayout.addView(t);
                }
            }
        } catch (Exception e) {
            TextView t = new TextView(this);
            t.setText("Error: " + e.getMessage());
            listLayout.addView(t);
        }
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        Toast.makeText(this, "Ab dobara Start dabao", Toast.LENGTH_SHORT).show();
    }
            }
