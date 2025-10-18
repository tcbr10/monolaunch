package com.monobogdan.monolaunch;


import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsoluteLayout;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.GridLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.os.Build;

import java.util.Collections;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;


class AppListView extends GridView
{
    class AppInfo
    {
        public Drawable icon;
        public String name;
        public Intent intent;
    }


    class PackageManagerListener extends BroadcastReceiver
    {


        @Override
        public void onReceive(Context context, Intent intent) {
            fetchAppList();
            rebuildUI();


            System.gc();
        }
    }


    private Launcher parent;
    private ArrayList<AppInfo> installedApps;
    private int selectedItem;
    List<View> widgetList = new ArrayList<>();


    // private void fetchAppList()
    // {
    //     installedApps.clear();


    //     Intent filter = new Intent();
    //     filter.setAction(Intent.ACTION_MAIN);
    //     filter.addCategory(Intent.CATEGORY_LAUNCHER);


    //     PackageManager pm = getContext().getPackageManager();
    //     //List<ResolveInfo> apps = pm.queryIntentActivities(filter, 0);
    //     List<ApplicationInfo> apps = pm.getInstalledApplications(0);


    //     for (ApplicationInfo info:
    //             apps) {
    //         AppInfo app = new AppInfo();
    //         ;
    //         app.name = info.loadLabel(pm).toString();
    //         app.icon = info.loadIcon(pm);
    //         app.intent = pm.getLaunchIntentForPackage(info.packageName);


    //         if(app.intent == null)
    //             continue;


    //         Log.i("Test", "fetchAppList: " + String.format("%s %s", app.name, app.intent));
    //         installedApps.add(app);
    //     }
    // }

    private void fetchAppList() {
    installedApps.clear();

    Intent filter = new Intent(Intent.ACTION_MAIN);
    filter.addCategory(Intent.CATEGORY_LAUNCHER);

    PackageManager pm = getContext().getPackageManager();
    
    List<ResolveInfo> apps;
    
    // Handle Android 13+ (API 33) deprecated method
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        apps = pm.queryIntentActivities(
            filter, 
            PackageManager.ResolveInfoFlags.of(0)
        );
    } else {
        apps = pm.queryIntentActivities(filter, 0);
    }

    for (ResolveInfo resolveInfo : apps) {
        AppInfo app = new AppInfo();
        
        app.name = resolveInfo.loadLabel(pm).toString();
        app.icon = resolveInfo.loadIcon(pm);
        app.intent = pm.getLaunchIntentForPackage(
            resolveInfo.activityInfo.packageName
        );

        if (app.intent == null)
            continue;

        Log.i("Test", "fetchAppList: " + app.name + " " + app.intent);
        installedApps.add(app);
    }
    
    // Sort alphabetically
    Collections.sort(installedApps, new Comparator<AppInfo>() {
        @Override
        public int compare(AppInfo a1, AppInfo a2) {
            return a1.name.compareToIgnoreCase(a2.name);
        }
    });
}


    public AppListView(Launcher launcher)
    {
        super(launcher.getApplicationContext());


        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("", "onItemSelected: ");
            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {


            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        launcher.getApplicationContext().registerReceiver(new PackageManagerListener(), filter);


        parent = launcher;


        installedApps = new ArrayList<>();
        fetchAppList();
        rebuildUI();
    }

    // Helper method to safely convert any Drawable to Bitmap
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        }

        // If it's already a BitmapDrawable, extract the bitmap directly
        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) {
                return bitmap;
            }
        }

        // For AdaptiveIconDrawable and other drawable types, render to a bitmap
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        
        // Ensure valid dimensions
        if (width <= 0) width = 48;
        if (height <= 0) height = 48;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }


    private void rebuildUI()
    {
        setNumColumns(3);
        setColumnWidth(48);
        setClickable(true);
        setSelector(R.drawable.none);
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(view != null) {
                    view.animate().scaleX(1.1f).scaleY(1.2f).setDuration(100);


                    for (View v:
                         widgetList) {
                        if(v != view)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200);
                    }
                }



            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                setSelection(installedApps.size() - 1);
            }
        });


        for(int i = 0; i < installedApps.size(); i++)
        {
            AppInfo app = installedApps.get(i);


            ImageButton button = new ImageButton(getContext());
            button.setBackgroundColor(Color.TRANSPARENT);
            button.setFocusable(true);

            // FIXED: Use drawableToBitmap helper to safely convert any Drawable (including AdaptiveIconDrawable)
            Bitmap iconBitmap = drawableToBitmap(app.icon);
            if (iconBitmap != null) {
                button.setImageBitmap(Bitmap.createScaledBitmap(iconBitmap, 36, 36, false));
            }
            widgetList.add(button);
        }


        setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return installedApps.size();
            }


            @Override
            public Object getItem(int position) {
                return null;
            }


            @Override
            public long getItemId(int position) {
                return 0;
            }


            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return widgetList.get(position);
            }
        });
    }


    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);


        Log.i("TAG", "onItemSelected: " + gainFocus);
        // HACK: When appList loses focus (possible bug in GridView), return focus to last element
        setSelection(installedApps.size() - 1);
        requestFocus();
    }


    private int clamp(int a, int min, int max)
    {
        return a < min ? min : (a > max ? max : a);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER)
            getContext().startActivity(installedApps.get(getSelectedItemPosition()).intent);


        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            parent.switchToHome();
            return true;
        }


        return super.onKeyUp(keyCode, event);
    }
}
