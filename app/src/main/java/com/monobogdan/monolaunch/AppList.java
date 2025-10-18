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

// new imports
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;
import android.view.ViewGroup.LayoutParams;

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
        // use density-aware sizing
        final float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        // Choose icon size based on screen size: ensure visible on very small screens
        int iconDp = 64; // base
        // on tiny screens scale down a bit, otherwise keep or increase
        int approxMinSideDp = Math.min(screenW, screenH) / (int)density;
        if (approxMinSideDp <= 240) {
            iconDp = 56;
        } else if (approxMinSideDp >= 720) {
            iconDp = 96;
        }

        final int iconSizePx = (int)(iconDp * density + 0.5f);
        final int labelHeightPx = (int)(18 * density + 0.5f);
        final int itemPadding = (int)(6 * density + 0.5f);

        setNumColumns(3);
        setColumnWidth(iconSizePx + itemPadding * 2);
        setClickable(true);
        setSelector(R.drawable.none);

        // clear old widgets
        widgetList.clear();

        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if(view != null) {
                    view.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120);

                    for (View v:
                         widgetList) {
                        if(v != view)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200);
                    }
                }



            }


            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if(installedApps.size() > 0)
                    setSelection(installedApps.size() - 1);
            }
        });


        for(int i = 0; i < installedApps.size(); i++)
        {
            final AppInfo app = installedApps.get(i);

            // container: vertical linear layout (icon above, label below)
            LinearLayout container = new LinearLayout(getContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            container.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
            container.setLayoutParams(new LayoutParams(
                    iconSizePx + itemPadding * 2,
                    LayoutParams.WRAP_CONTENT
            ));
            container.setClickable(true);
            container.setFocusable(true);

            ImageView iv = new ImageView(getContext());
            iv.setBackgroundColor(Color.TRANSPARENT);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
            ivLp.gravity = Gravity.CENTER_HORIZONTAL;
            iv.setLayoutParams(ivLp);

            // convert drawable -> bitmap then scale (keeps adaptive icons etc.)
            Bitmap iconBitmap = drawableToBitmap(app.icon);
            if (iconBitmap != null) {
                iv.setImageBitmap(Bitmap.createScaledBitmap(iconBitmap, iconSizePx, iconSizePx, true));
            }

            TextView label = new TextView(getContext());
            label.setText(app.name);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setTextColor(Color.WHITE);
            label.setGravity(Gravity.CENTER);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, labelHeightPx
            );
            tvLp.topMargin = (int)(4 * density);
            label.setLayoutParams(tvLp);

            // modern clean look: subtle shadow (if desired) and transparent bg
            label.setShadowLayer(1.0f, 0, 1.0f, Color.argb(120,0,0,0));

            container.addView(iv);
            container.addView(label);

            // touch: launch app on click
            final Intent launchIntent = app.intent;
            container.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        getContext().startActivity(launchIntent);
                    } catch (Exception ex) {
                        Log.e("AppListView", "Failed to launch " + app.name, ex);
                    }
                }
            });

            // focus scaling for D-pad / remote
            container.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120);
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160);
                    }
                }
            });

            widgetList.add(container);
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

        // click support for grid items (redundant with container onClick, but helps touch on empty areas)
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parentView, View view, int position, long id) {
                if (position >= 0 && position < installedApps.size()) {
                    try {
                        getContext().startActivity(installedApps.get(position).intent);
                    } catch (Exception ex) {
                        Log.e("AppListView", "Failed to launch on item click", ex);
                    }
                }
            }
        });
    }


    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);


        Log.i("TAG", "onItemSelected: " + gainFocus);
        // HACK: When appList loses focus (possible bug in GridView), return focus to last element
        if (installedApps.size() > 0)
            setSelection(installedApps.size() - 1);
        requestFocus();
    }


    private int clamp(int a, int min, int max)
    {
        return a < min ? min : (a > max ? max : a);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            int sel = getSelectedItemPosition();
            if(sel >= 0 && sel < installedApps.size()) {
                getContext().startActivity(installedApps.get(sel).intent);
            }
        }


        if(keyCode == KeyEvent.KEYCODE_BACK)
        {
            parent.switchToHome();
            return true;
        }


        return super.onKeyUp(keyCode, event);
    }
}
