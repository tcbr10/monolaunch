package com.monobogdan.monolaunch;

import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Tasks extends ListView {

    class AppTask {
        public String name;
        public String packageName;
        public Bitmap icon;
        public int id;
    }

    private Launcher launcher;
    private BaseAdapter adapterImpl;
    private ArrayList<AppTask> tasks;

    // Track current DPAD selection
    private int selectedIndex = 0;

    public Tasks(Launcher launcher) {
        super(launcher);  // USE ACTIVITY CONTEXT, NOT APPLICATION CONTEXT
        this.launcher = launcher;
        setBackgroundColor(Color.BLACK);

        tasks = new ArrayList<>();

        adapterImpl = new BaseAdapter() {
            @Override
            public int getCount() {
                return tasks.isEmpty() ? 1 : tasks.size();
            }

            @Override
            public Object getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @SuppressLint("MissingInflatedId")
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (tasks.isEmpty()) {
                    TextView empty = new TextView(getContext());
                    empty.setText("No apps available.\nPlease grant Usage Access in Settings.");
                    empty.setTextColor(Color.WHITE);
                    empty.setTextSize(18f);
                    empty.setPadding(50, 50, 50, 50);
                    return empty;
                }

                AppTask task = tasks.get(position);
                View view = launcher.getLayoutInflater().inflate(R.layout.task, parent, false);
                ((ImageView) view.findViewById(R.id.app_icon)).setImageBitmap(task.icon);
                ((TextView) view.findViewById(R.id.app_name)).setText(task.name);

                // Highlight currently selected DPAD item
                if (position == selectedIndex) {
                    view.setBackgroundColor(Color.DKGRAY);
                } else {
                    view.setBackgroundColor(Color.TRANSPARENT);
                }

                return view;
            }
        };

        setAdapter(adapterImpl);
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void updateTaskList() {
        tasks.clear();
        selectedIndex = 0;
        PackageManager pacMan = getContext().getPackageManager();

        UsageStatsManager usageStatsManager = (UsageStatsManager) getContext().getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();

        List<UsageStats> stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60 * 60 * 24, now
        );

        if (stats == null || stats.isEmpty()) {
            Log.d("Tasks", "No apps found or Usage Access not granted!");
            adapterImpl.notifyDataSetChanged();
            return;
        }

        Collections.sort(stats, (a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));

        int count = 0;
        for (UsageStats us : stats) {
            if (count >= 10) break;
            String pkgName = us.getPackageName();

            if (pkgName.equals("com.monobogdan.monolaunch") || pkgName.equals("com.sprd.simple.launcher"))
                continue;

            try {
                PackageInfo pacInfo = pacMan.getPackageInfo(pkgName, 0);
                AppTask appInfo = new AppTask();
                appInfo.name = pacMan.getApplicationLabel(pacInfo.applicationInfo).toString();
                appInfo.packageName = pkgName;
                
                // FIXED: Safe icon handling for AdaptiveIconDrawable
                try {
                    Drawable appIcon = pacInfo.applicationInfo.loadIcon(pacMan);
                    Bitmap iconBitmap;
                    
                    if (appIcon instanceof BitmapDrawable) {
                        iconBitmap = ((BitmapDrawable) appIcon).getBitmap();
                    } else {
                        // Handle AdaptiveIconDrawable and other types
                        int size = (int) (48 * getResources().getDisplayMetrics().density);
                        iconBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(iconBitmap);
                        appIcon.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
                        appIcon.draw(canvas);
                    }
                    
                    appInfo.icon = iconBitmap;
                    
                } catch (Exception e) {
                    Log.e("Tasks", "Error loading icon for " + pkgName + ": " + e.getMessage());
                    appInfo.icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
                }
                
                appInfo.id = count;
                tasks.add(appInfo);
                count++;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e("Tasks", "Package not found: " + pkgName);
            }
        }

        adapterImpl.notifyDataSetChanged();

        // Auto-select first item
        if (!tasks.isEmpty()) {
            setSelection(selectedIndex);
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (tasks.isEmpty()) {
            return super.onKeyUp(keyCode, event);
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                launcher.switchToHome();
                return true;

            case KeyEvent.KEYCODE_DPAD_CENTER:
                Intent open = getContext().getPackageManager().getLaunchIntentForPackage(
                        tasks.get(selectedIndex).packageName
                );
                if (open != null) {
                    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getContext().startActivity(open);
                } else {
                    Log.d("Tasks", "App not found: " + tasks.get(selectedIndex).packageName);
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (selectedIndex > 0) selectedIndex--;
                adapterImpl.notifyDataSetChanged();
                setSelection(selectedIndex);
                return true;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (selectedIndex < tasks.size() - 1) selectedIndex++;
                adapterImpl.notifyDataSetChanged();
                setSelection(selectedIndex);
                return true;
        }

        return super.onKeyUp(keyCode, event);
    }
}
