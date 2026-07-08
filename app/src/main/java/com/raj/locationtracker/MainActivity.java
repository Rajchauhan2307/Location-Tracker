package com.raj.locationtracker;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
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

    TextView statusText, placesVal, kmVal, intervalVal;
    LinearLayout listLayout;
    SharedPreferences prefs;
    int selectedMinutes = 5;
    Button[] intervalBtns;
    int[] intervalOptions = {1, 5, 15, 30, 60};
    Button startBtn, stopBtn;

    // ---- Colour palette ----
    final int BG = Color.parseColor("#0d0b1f");
    final int CARD = Color.parseColor("#1a1730");
    final int BORDER = Color.parseColor("#2d2850");
    final int PURPLE = Color.parseColor("#a855f7");
    final int PINK = Color.parseColor("#ec4899");
    final int BLUE = Color.parseColor("#3b82f6");
    final int CYAN = Color.parseColor("#22d3ee");
    final int GREEN = Color.parseColor("#22c55e");
    final int TXT = Color.parseColor("#f1f0ff");
    final int MUTED = Color.parseColor("#8b86b8");
    final int RED = Color.parseColor("#f43f5e");
    final int DULL = Color.parseColor("#241f3d");
    final int DULLTXT = Color.parseColor("#5a5480");

    final SimpleDateFormat STAMP = new SimpleDateFormat("yyyy-MM-dd  HH:mm", Locale.US);
    final SimpleDateFormat AMPM = new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US);
    final SimpleDateFormat AMPM_TIME = new SimpleDateFormat("hh:mm a", Locale.US);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("tracker_prefs", MODE_PRIVATE);
        selectedMinutes = prefs.getInt("interval_min", 5);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(24), dp(18), dp(24));
        scroll.addView(root);

        // Header
        TextView title = new TextView(this);
        title.setText("Location Tracker");
        title.setTextSize(26);
        title.setTextColor(PINK);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Your day, mapped automatically");
        subtitle.setTextSize(12);
        subtitle.setTextColor(MUTED);
        subtitle.setPadding(0, dp(2), 0, dp(16));
        root.addView(subtitle);

        // Status + Stats card
        LinearLayout statusCard = gradientCard();
        statusCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(GREEN);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotLp.rightMargin = dp(10);
        dot.setLayoutParams(dotLp);
        statusRow.addView(dot);
        statusText = new TextView(this);
        statusText.setText("Tracking Off");
        statusText.setTextSize(16);
        statusText.setTextColor(Color.WHITE);
        statusText.setTypeface(null, android.graphics.Typeface.BOLD);
        statusRow.addView(statusText);
        statusCard.addView(statusRow);

        LinearLayout statsRow = new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setPadding(0, dp(16), 0, 0);
        placesVal = statBlock(statsRow, "0", "PLACES", CYAN);
        kmVal = statBlock(statsRow, "0.0", "KM MOVED", PINK);
        intervalVal = statBlock(statsRow, selectedMinutes + "m", "INTERVAL", PURPLE);
        statusCard.addView(statsRow);
        root.addView(statusCard);

        // Interval selector
        root.addView(sectionLabel("SAVE LOCATION EVERY"));
        LinearLayout intBox = plainCard();
        LinearLayout pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.HORIZONTAL);
        intervalBtns = new Button[intervalOptions.length];
        for (int i = 0; i < intervalOptions.length; i++) {
            final int idx = i;
            Button pill = new Button(this);
            int m = intervalOptions[i];
            pill.setText(m < 60 ? m + "m" : "1h");
            pill.setTextSize(12);
            pill.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(44), 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            pill.setLayoutParams(lp);
            pill.setPadding(0, 0, 0, 0);
            pill.setOnClickListener(v -> {
                selectedMinutes = intervalOptions[idx];
                prefs.edit().putInt("interval_min", selectedMinutes).apply();
                intervalVal.setText(selectedMinutes < 60 ? selectedMinutes + "m" : "1h");
                refreshPills();
                Toast.makeText(this, "Interval set: " + (selectedMinutes < 60 ? selectedMinutes + " min" : "1 hour"), Toast.LENGTH_SHORT).show();
            });
            intervalBtns[i] = pill;
            pills.addView(pill);
        }
        intBox.addView(pills);
        root.addView(intBox);
        refreshPills();

        // Start / Stop buttons
        startBtn = new Button(this);
        startBtn.setText("▶  START TRACKING");
        startBtn.setTextSize(15);
        startBtn.setAllCaps(false);
        startBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        sLp.topMargin = dp(12);
        startBtn.setLayoutParams(sLp);
        startBtn.setOnClickListener(v -> {
            if (prefs.getBoolean("is_tracking", false)) {
                Toast.makeText(this, "Already tracking!", Toast.LENGTH_SHORT).show();
                return;
            }
            if (checkPerms()) {
                prefs.edit().putBoolean("is_tracking", true).apply();
                Intent svc = new Intent(this, TrackingService.class);
                svc.putExtra("interval_min", selectedMinutes);
                startService(svc);
                statusText.setText("Tracking Active");
                updateButtonStates();
                Toast.makeText(this, "Tracking started!", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(startBtn);

        stopBtn = new Button(this);
        stopBtn.setText("⏹  STOP TRACKING");
        stopBtn.setTextSize(15);
        stopBtn.setAllCaps(false);
        stopBtn.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams stLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        stLp.topMargin = dp(10);
        stopBtn.setLayoutParams(stLp);
        stopBtn.setOnClickListener(v -> {
            if (!prefs.getBoolean("is_tracking", false)) {
                Toast.makeText(this, "Tracking is already off", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putBoolean("is_tracking", false).apply();
            prefs.edit().putString("last_stop_reason", "manual").apply();
            prefs.edit().putLong("last_stop_time", System.currentTimeMillis()).apply();
            stopService(new Intent(this, TrackingService.class));
            statusText.setText("Tracking Off");
            updateButtonStates();
            Toast.makeText(this, "Tracking stopped", Toast.LENGTH_SHORT).show();
        });
        root.addView(stopBtn);

        // Row: Timeline + Clear
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(halfButton("📖  Show Data", CYAN, v -> askRangeThen(false), true));
        row1.addView(halfButton("🧹  Clear Screen", PURPLE, v -> {
            listLayout.removeAllViews();
            emptyMsg("Screen cleared. Data is safe — tap Show Data to view.");
            Toast.makeText(this, "Cleared from screen (data safe)", Toast.LENGTH_SHORT).show();
        }, false));
        root.addView(row1);

        // Row: Today quick + Export
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(halfButton("📅  Today", PINK, v -> {
            showReportOnScreen(startOfToday(), endOfToday(), "Today");
        }, true));
        row2.addView(halfButton("📄  Export", BLUE, v -> askRangeThen(true), false));
        root.addView(row2);

        // Timeline label
        root.addView(sectionLabel("REPORT / TIMELINE"));
        listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(listLayout);

        setContentView(scroll);

        emptyMsg("Tap 📅 Today or 📖 Show Data to view your report.");
        updateStatsOnly();
        updateButtonStates();
        checkLocationOn();
    }

    // ========== FROM/TO RANGE PICKER (date + time, AM/PM, type-mode) ==========
    void askRangeThen(final boolean isExport) {
        // Step 1: FROM date
        Calendar now = Calendar.getInstance();
        Toast.makeText(this, "Select FROM (start) date & time", Toast.LENGTH_SHORT).show();
        DatePickerDialog fromDate = new DatePickerDialog(this, (view, y, mo, d) -> {
            final Calendar from = Calendar.getInstance();
            from.set(y, mo, d, 0, 0, 0);
            TimePickerDialog fromTime = new TimePickerDialog(this, (tv, hh, mm) -> {
                from.set(Calendar.HOUR_OF_DAY, hh);
                from.set(Calendar.MINUTE, mm);
                from.set(Calendar.SECOND, 0);
                final long fromMs = from.getTimeInMillis();
                // Step 2: TO date
                Toast.makeText(this, "Now select TO (end) date & time", Toast.LENGTH_SHORT).show();
                DatePickerDialog toDate = new DatePickerDialog(this, (view2, y2, mo2, d2) -> {
                    final Calendar to = Calendar.getInstance();
                    to.set(y2, mo2, d2, 23, 59, 59);
                    TimePickerDialog toTime = new TimePickerDialog(this, (tv2, hh2, mm2) -> {
                        to.set(Calendar.HOUR_OF_DAY, hh2);
                        to.set(Calendar.MINUTE, mm2);
                        to.set(Calendar.SECOND, 59);
                        long toMs = to.getTimeInMillis();
                        String label = AMPM.format(new Date(fromMs)) + "  →  " + AMPM.format(new Date(toMs));
                        if (isExport) exportReport(fromMs, toMs, label);
                        else showReportOnScreen(fromMs, toMs, label);
                    }, 23, 59, false); // false = AM/PM system
                    toTime.setTitle("TO (end) time");
                    toTime.show();
                }, y, mo, d);
                toDate.setTitle("TO (end) date");
                toDate.show();
            }, 0, 0, false); // false = AM/PM system
            fromTime.setTitle("FROM (start) time");
            fromTime.show();
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        fromDate.setTitle("FROM (start) date");
        fromDate.show();
    }

    // ========== BUILD DETAILED REPORT (shared by screen + export) ==========
    List<String> readEntriesInRange(long fromMs, long toMs) {
        List<String> out = new ArrayList<>();
        try {
            File f = new File(getFilesDir(), "locations.txt");
            if (!f.exists()) return out;
            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.length() < 17) continue;
                try {
                    long t = STAMP.parse(line.substring(0, 17)).getTime();
                    if (t >= fromMs && t <= toMs) out.add(line);
                } catch (Exception ignore) {}
            }
            br.close();
        } catch (Exception e) {}
        return out;
    }

    // returns a text report string
    String buildReportText(List<String> entries, String rangeLabel) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════╗\n");
        sb.append("   📍 LOCATION REPORT\n");
        sb.append("╚══════════════════════════╝\n\n");
        sb.append("🗓️ Range: ").append(rangeLabel).append("\n\n");

        if (entries.isEmpty()) {
            sb.append("No data found in this range.\n");
            return sb.toString();
        }

        // SUMMARY
        String firstTime = "", lastTime = "";
        Set<String> places = new LinkedHashSet<>();
        try {
            firstTime = AMPM_TIME.format(STAMP.parse(entries.get(0).substring(0, 17)));
            lastTime = AMPM_TIME.format(STAMP.parse(entries.get(entries.size() - 1).substring(0, 17)));
        } catch (Exception e) {}
        for (String l : entries) {
            String p = placeOf(l);
            if (!p.isEmpty()) places.add(p);
        }
        sb.append("📊 SUMMARY\n─────────────────────\n");
        sb.append("Total Points: ").append(entries.size()).append("\n");
        sb.append("Places Visited: ").append(places.size()).append("\n");
        sb.append("First: ").append(firstTime).append("\n");
        sb.append("Last: ").append(lastTime).append("\n\n");

        // STAY DURATION (group consecutive same place)
        sb.append("⏱️ STAY DURATION\n─────────────────────\n");
        String curPlace = null; long curStart = 0, curEnd = 0;
        for (String l : entries) {
            String p = placeOf(l);
            long t = 0;
            try { t = STAMP.parse(l.substring(0, 17)).getTime(); } catch (Exception e) { continue; }
            if (curPlace == null) { curPlace = p; curStart = t; curEnd = t; }
            else if (p.equals(curPlace)) { curEnd = t; }
            else {
                sb.append("📍 ").append(curPlace).append(" — ").append(durationText(curStart, curEnd)).append("\n");
                sb.append("   (").append(AMPM_TIME.format(new Date(curStart))).append(" - ").append(AMPM_TIME.format(new Date(curEnd))).append(")\n");
                curPlace = p; curStart = t; curEnd = t;
            }
        }
        if (curPlace != null) {
            sb.append("📍 ").append(curPlace).append(" — ").append(durationText(curStart, curEnd)).append("\n");
            sb.append("   (").append(AMPM_TIME.format(new Date(curStart))).append(" - ").append(AMPM_TIME.format(new Date(curEnd))).append(")\n");
        }
        sb.append("\n");

        // STOP EVENTS
        sb.append("🔍 STOP EVENTS\n─────────────────────\n");
        String reason = prefs.getString("last_stop_reason", "none");
        long stopTime = prefs.getLong("last_stop_time", 0);
        if (!reason.equals("none") && stopTime > 0) {
            String when = AMPM.format(new Date(stopTime));
            if (reason.equals("manual"))
                sb.append("✅ ").append(when).append(" — Stopped by you (manual)\n");
            else
                sb.append("⚠️ ").append(when).append(" — Stopped automatically (app was killed!)\n");
        } else {
            sb.append("No stop events recorded.\n");
        }
        sb.append("\n");

        // FULL TIMELINE
        sb.append("📅 FULL TIMELINE\n─────────────────────\n");
        for (String l : entries) {
            String tm = "", pl = placeOf(l), co = coordsOf(l);
            try { tm = AMPM_TIME.format(STAMP.parse(l.substring(0, 17))); } catch (Exception e) {}
            sb.append("🕐 ").append(tm).append("\n");
            sb.append("📍 ").append(pl).append("\n");
            if (!co.isEmpty()) sb.append("   (").append(co).append(")\n");
            sb.append("\n");
        }
        return sb.toString();
    }

    String placeOf(String l) {
        int arrow = l.indexOf("→");
        int paren = l.indexOf("(");
        if (arrow >= 0 && paren > arrow) return l.substring(arrow + 1, paren).trim();
        if (arrow >= 0) return l.substring(arrow + 1).trim();
        return "";
    }
    String coordsOf(String l) {
        int s = l.indexOf('(');
        int e = l.indexOf(')');
        if (s >= 0 && e > s) return l.substring(s + 1, e).trim();
        return "";
    }
    String durationText(long start, long end) {
        long mins = Math.max(0, (end - start) / 60000);
        if (mins < 60) return mins + " min";
        long h = mins / 60, m = mins % 60;
        return h + " hr" + (m > 0 ? " " + m + " min" : "");
    }

    // ========== SHOW REPORT ON SCREEN ==========
    void showReportOnScreen(long fromMs, long toMs, String label) {
        listLayout.removeAllViews();
        List<String> entries = readEntriesInRange(fromMs, toMs);
        if (entries.isEmpty()) { emptyMsg("No data found in this range."); return; }

        // Update top stats
        Set<String> places = new LinkedHashSet<>();
        for (String l : entries) { String p = placeOf(l); if (!p.isEmpty()) places.add(p); }
        placesVal.setText(String.valueOf(places.size()));

        // Report card (scrollable text)
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14)); g.setStroke(dp(1), BORDER);
        card.setBackground(g);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        card.setLayoutParams(clp);

        TextView report = new TextView(this);
        report.setText(buildReportText(entries, label));
        report.setTextColor(TXT);
        report.setTextSize(13);
        report.setTypeface(android.graphics.Typeface.MONOSPACE);
        card.addView(report);
        listLayout.addView(card);

        // Then individual entries with map buttons (reverse = newest first)
        List<String> rev = new ArrayList<>(entries);
        Collections.reverse(rev);
        for (String l : rev) addEntry(l);
    }

    // ========== EXPORT REPORT ==========
    void exportReport(long fromMs, long toMs, String label) {
        try {
            List<String> entries = readEntriesInRange(fromMs, toMs);
            if (entries.isEmpty()) { Toast.makeText(this, "No data in this range", Toast.LENGTH_SHORT).show(); return; }
            String text = buildReportText(entries, label);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_SUBJECT, "My Location Report");
            share.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(share, "Export Report"));
        } catch (Exception e) { Toast.makeText(this, "Error exporting", Toast.LENGTH_LONG).show(); }
    }

    // ---- Smart button state ----
    void updateButtonStates() {
        boolean tracking = prefs.getBoolean("is_tracking", false);
        GradientDrawable sg = new GradientDrawable();
        sg.setCornerRadius(dp(16));
        if (!tracking) {
            sg.setColors(new int[]{GREEN, CYAN});
            sg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            startBtn.setTextColor(Color.parseColor("#04121a"));
        } else { sg.setColor(DULL); startBtn.setTextColor(DULLTXT); }
        startBtn.setBackground(sg);
        GradientDrawable tg = new GradientDrawable();
        tg.setCornerRadius(dp(16));
        if (tracking) {
            tg.setColors(new int[]{RED, PINK});
            tg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            stopBtn.setTextColor(Color.WHITE);
        } else { tg.setColor(DULL); stopBtn.setTextColor(DULLTXT); }
        stopBtn.setBackground(tg);
    }

    void refreshPills() {
        for (int i = 0; i < intervalBtns.length; i++) {
            boolean on = intervalOptions[i] == selectedMinutes;
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            if (on) {
                g.setColors(new int[]{PURPLE, PINK});
                g.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
                intervalBtns[i].setTextColor(Color.WHITE);
            } else { g.setColor(DULL); intervalBtns[i].setTextColor(MUTED); }
            intervalBtns[i].setBackground(g);
        }
    }

    void checkLocationOn() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            boolean gps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (!gps) {
                new AlertDialog.Builder(this)
                    .setTitle("Location is OFF")
                    .setMessage("Please turn ON location to track. Open settings now?")
                    .setPositiveButton("Turn ON", (d, w) ->
                        startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Later", null).show();
            }
        } catch (Exception e) {}
    }

    long startOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0);
        return c.getTimeInMillis();
    }
    long endOfToday() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59); c.set(Calendar.SECOND, 59);
        return c.getTimeInMillis();
    }

    void updateStatsOnly() {
        try {
            File f = new File(getFilesDir(), "locations.txt");
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            Set<String> places = new HashSet<>();
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.startsWith(today)) {
                        String p = placeOf(line);
                        if (!p.isEmpty()) places.add(p);
                    }
                }
                br.close();
            }
            placesVal.setText(String.valueOf(places.size()));
            String kmKey = "km_" + today;
            float km = prefs.getFloat(kmKey, 0f);
            kmVal.setText(String.format(Locale.US, "%.1f", km));
        } catch (Exception e) {}
    }

    void addEntry(String l) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14)); g.setStroke(dp(1), BORDER);
        item.setBackground(g);
        item.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        item.setLayoutParams(lp);

        TextView t = new TextView(this);
        t.setText("🕒  " + l);
        t.setTextColor(TXT);
        t.setTextSize(13);
        item.addView(t);

        Button mapBtn = new Button(this);
        mapBtn.setText("🗺️ View on Map");
        mapBtn.setTextSize(11);
        mapBtn.setAllCaps(false);
        mapBtn.setTextColor(CYAN);
        GradientDrawable mg = new GradientDrawable();
        mg.setColor(DULL); mg.setCornerRadius(dp(8));
        mapBtn.setBackground(mg);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
        mlp.topMargin = dp(8);
        mapBtn.setLayoutParams(mlp);
        mapBtn.setPadding(dp(14), 0, dp(14), 0);
        final String entry = l;
        mapBtn.setOnClickListener(v -> openMap(entry));
        item.addView(mapBtn);

        listLayout.addView(item);
    }

    void openMap(String entry) {
        try {
            String coords = coordsOf(entry).replace(" ", "");
            if (!coords.isEmpty()) {
                Uri uri = Uri.parse("geo:" + coords + "?q=" + coords);
                Intent map = new Intent(Intent.ACTION_VIEW, uri);
                map.setPackage("com.google.android.apps.maps");
                if (map.resolveActivity(getPackageManager()) != null) startActivity(map);
                else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://maps.google.com/?q=" + coords)));
            }
        } catch (Exception ex) { Toast.makeText(this, "Can't open map", Toast.LENGTH_SHORT).show(); }
    }

    void emptyMsg(String msg) {
        TextView t = new TextView(this);
        t.setText(msg);
        t.setTextColor(MUTED);
        t.setTextSize(13);
        t.setPadding(0, dp(10), 0, 0);
        listLayout.addView(t);
    }

    // ---- UI helpers ----
    int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density); }

    LinearLayout gradientCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColors(new int[]{Color.parseColor("#2d1b4e"), Color.parseColor("#1a1730")});
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), Color.parseColor("#3d2d5e"));
        c.setBackground(g);
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    LinearLayout plainCard() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(16)); g.setStroke(dp(1), BORDER);
        c.setBackground(g);
        c.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    TextView statBlock(LinearLayout parent, String num, String label, int colour) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView n = new TextView(this);
        n.setText(num); n.setTextSize(22); n.setTextColor(colour);
        n.setTypeface(null, android.graphics.Typeface.BOLD);
        n.setGravity(Gravity.CENTER);
        col.addView(n);
        TextView lb = new TextView(this);
        lb.setText(label); lb.setTextSize(9); lb.setTextColor(MUTED);
        lb.setGravity(Gravity.CENTER);
        col.addView(lb);
        parent.addView(col);
        return n;
    }

    TextView sectionLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(10); t.setTextColor(MUTED);
        t.setPadding(dp(4), dp(18), 0, dp(10));
        t.setLetterSpacing(0.15f);
        return t;
    }

    Button halfButton(String text, int textColor, View.OnClickListener click, boolean leftMargin) {
        Button btn = new Button(this);
        btn.setText(text); btn.setTextSize(12); btn.setAllCaps(false);
        btn.setTextColor(textColor);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD); g.setCornerRadius(dp(14)); g.setStroke(dp(1), BORDER);
        btn.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        lp.topMargin = dp(8);
        if (leftMargin) lp.rightMargin = dp(4); else lp.leftMargin = dp(4);
        btn.setLayoutParams(lp);
        btn.setPadding(0, 0, 0, 0);
        btn.setOnClickListener(click);
        return btn;
    }

    // ---- Permissions ----
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
            Toast.makeText(this, "Grant permission, then tap Start again", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int rc, @NonNull String[] p, @NonNull int[] r) {
        super.onRequestPermissionsResult(rc, p, r);
        Toast.makeText(this, "Now tap Start again", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs != null) {
            statusText.setText(prefs.getBoolean("is_tracking", false) ? "Tracking Active" : "Tracking Off");
            updateStatsOnly();
            updateButtonStates();
        }
    }
}
