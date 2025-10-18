package com.monobogdan.monolaunch;

import android.annotation.SuppressLint;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
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

    public Tasks(Launcher launcher) {
        super(launcher.getApplicationContext());
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

                return view;
            }
        };

        setAdapter(adapterImpl);
    }

    public void updateTaskList() {
        tasks.clear();
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
                appInfo.icon = ((BitmapDrawable) pacInfo.applicationInfo.loadIcon(pacMan)).getBitmap();
                appInfo.id = count;
                tasks.add(appInfo);
                count++;
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        adapterImpl.notifyDataSetChanged();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            launcher.switchToHome();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER && !tasks.isEmpty()) {
            if (getSelectedItemPosition() < 0 || getSelectedItemPosition() >= tasks.size())
                return super.onKeyUp(keyCode, event);

            Intent open = getContext().getPackageManager().getLaunchIntentForPackage(
                    tasks.get(getSelectedItemPosition()).packageName
            );
            if (open != null) {
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getContext().startActivity(open);
            } else {
                Log.d("TAG", "App not found");
            }
        }
        return super.onKeyUp(keyCode, event);
    }
}
