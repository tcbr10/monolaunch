package com.monobogdan.monolaunch;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

class AppListView extends GridView {

    class AppInfo {
        public Drawable icon;
        public String name;
        public Intent intent;
    }

    class PackageManagerListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            fetchAppList();
            rebuildUI();
            System.gc();
        }
    }

    private Launcher parent;
    private ArrayList<AppInfo> installedApps;
    private ArrayList<AppInfo> fullAppList; // Master list for filtering
    
    private List<View> widgetList = new ArrayList<>();

    // T9 Search Variables
    private StringBuilder t9Query = new StringBuilder();
    private TextView searchBar;
    private long lastT9KeyTime = 0;
    private static final long T9_TIMEOUT = 2000; // Reset search after 2 seconds of inactivity

    // Define the Focus Border Drawable programmatically
    private GradientDrawable focusBorder;
    private GradientDrawable transparentBackground;

    public AppListView(Launcher launcher) {
        super(launcher.getApplicationContext());
        this.parent = launcher;
        this.installedApps = new ArrayList<>();
        this.fullAppList = new ArrayList<>();

        // Initialize Drawables for Focus
        focusBorder = new GradientDrawable();
        focusBorder.setShape(GradientDrawable.RECTANGLE);
        focusBorder.setColor(Color.TRANSPARENT);
        focusBorder.setStroke(6, 0xFFFFC107); // Yellow border, 6px width
        focusBorder.setCornerRadius(12f);

        transparentBackground = new GradientDrawable();
        transparentBackground.setShape(GradientDrawable.RECTANGLE);
        transparentBackground.setColor(Color.TRANSPARENT);
        transparentBackground.setCornerRadius(12f);

        // Create Search Bar Overlay
        createSearchBar(launcher);

        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // Handled in OnFocusChangeListener for smoother grid nav
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        launcher.getApplicationContext().registerReceiver(new PackageManagerListener(), filter);

        fetchAppList();
        rebuildUI();
    }

    private void createSearchBar(Activity context) {
        searchBar = new TextView(context);
        searchBar.setBackgroundColor(0xEE000000); // Semi-transparent black
        searchBar.setTextColor(Color.WHITE);
        searchBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        searchBar.setPadding(30, 30, 30, 30);
        searchBar.setVisibility(View.GONE);
        searchBar.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        
        // Add to the parent activity's window on top of everything
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.TOP;
        context.addContentView(searchBar, params);
    }

    private void fetchAppList() {
        installedApps.clear();
        
        if(fullAppList != null) fullAppList.clear();
        else fullAppList = new ArrayList<>();

        Intent filter = new Intent(Intent.ACTION_MAIN);
        filter.addCategory(Intent.CATEGORY_LAUNCHER);

        PackageManager pm = getContext().getPackageManager();
        List<ResolveInfo> apps;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            apps = pm.queryIntentActivities(filter, PackageManager.ResolveInfoFlags.of(0));
        } else {
            apps = pm.queryIntentActivities(filter, 0);
        }

        for (ResolveInfo resolveInfo : apps) {
            AppInfo app = new AppInfo();
            app.name = resolveInfo.loadLabel(pm).toString();
            app.icon = resolveInfo.loadIcon(pm);
            app.intent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName);

            if (app.intent == null)
                continue;

            installedApps.add(app);
        }

        Collections.sort(installedApps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a1, AppInfo a2) {
                return a1.name.compareToIgnoreCase(a2.name);
            }
        });

        // Cache full list for T9 filtering
        fullAppList.addAll(installedApps);
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;
        if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        int width = drawable.getIntrinsicWidth();
        int height = drawable.getIntrinsicHeight();
        if (width <= 0) width = 48;
        if (height <= 0) height = 48;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void rebuildUI() {
        final float density = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;

        int iconDp = 64;
        int approxMinSideDp = Math.min(screenW, screenH) / (int) density;
        if (approxMinSideDp <= 240) iconDp = 56;
        else if (approxMinSideDp >= 720) iconDp = 96;

        final int iconSizePx = (int) (iconDp * density + 0.5f);
        final int labelHeightPx = (int) (18 * density + 0.5f);
        final int itemPadding = (int) (6 * density + 0.5f);

        setNumColumns(3);
        setColumnWidth(iconSizePx + itemPadding * 2);
        setClickable(true);
        setFocusable(true);
        
        // Remove default orange selector to use our custom border
        setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));

        widgetList.clear();

        for (int i = 0; i < installedApps.size(); i++) {
            final AppInfo app = installedApps.get(i);

            LinearLayout container = new LinearLayout(getContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            container.setPadding(itemPadding, itemPadding, itemPadding, itemPadding);
            container.setLayoutParams(new LayoutParams(
                    iconSizePx + itemPadding * 2,
                    LayoutParams.WRAP_CONTENT
            ));
            
            // IMPORTANT for D-pad: Make container focusable
            container.setFocusable(true);
            container.setClickable(true);
            // Set default background
            container.setBackground(transparentBackground);

            ImageView iv = new ImageView(getContext());
            iv.setBackgroundColor(Color.TRANSPARENT);
            iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            LinearLayout.LayoutParams ivLp = new LinearLayout.LayoutParams(iconSizePx, iconSizePx);
            ivLp.gravity = Gravity.CENTER_HORIZONTAL;
            iv.setLayoutParams(ivLp);

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
            tvLp.topMargin = (int) (4 * density);
            label.setLayoutParams(tvLp);
            label.setShadowLayer(1.0f, 0, 1.0f, Color.argb(120, 0, 0, 0));

            container.addView(iv);
            container.addView(label);

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

            container.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        // Apply Border and Scale Up
                        v.setBackground(focusBorder);
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100);
                    } else {
                        // Remove Border and Scale Down
                        v.setBackground(transparentBackground);
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                    }
                }
            });

            widgetList.add(container);
        }

        setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return installedApps.size(); }
            @Override
            public Object getItem(int position) { return installedApps.get(position); }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return widgetList.get(position);
            }
        });
    }

    // ==========================
    // T9 Search Logic Implementation
    // ==========================

    private char mapCharToT9(char c) {
        c = Character.toLowerCase(c);
        // English Mapping
        if (c >= 'a' && c <= 'c') return '2';
        if (c >= 'd' && c <= 'f') return '3';
        if (c >= 'g' && c <= 'i') return '4';
        if (c >= 'j' && c <= 'l') return '5';
        if (c >= 'm' && c <= 'o') return '6';
        if (c >= 'p' && c <= 's') return '7';
        if (c >= 't' && c <= 'v') return '8';
        if (c >= 'w' && c <= 'z') return '9';

        // Hebrew Mapping (Standard keypad logic)
        if (c == 'א' || c == 'ב' || c == 'ג') return '2';
        if (c == 'ד' || c == 'ה' || c == 'ו') return '3';
        if (c == 'ז' || c == 'ח' || c == 'ט') return '4';
        if (c == 'י' || c == 'כ' || c == 'ל' || c == 'ך') return '5';
        if (c == 'מ' || c == 'נ' || c == 'ס' || c == 'ם' || c == 'ן') return '6';
        if (c == 'ע' || c == 'פ' || c == 'צ' || c == 'ף' || c == 'ץ' || c == 'ק') return '7';
        if (c == 'ר' || c == 'ש' || c == 'ת') return '8';
        
        return 0;
    }

    private boolean matchesT9(String appName, String t9Digits) {
        if (t9Digits.isEmpty()) return true;
        
        // Extract just the letters from the app name to ignore spaces/symbols in matching
        StringBuilder cleanName = new StringBuilder();
        for (char c : appName.toCharArray()) {
            if (Character.isLetter(c)) cleanName.append(c);
        }
        
        String name = cleanName.toString().toLowerCase(Locale.getDefault());
        
        // Check if any substring of the name matches the digit sequence
        for (int i = 0; i <= name.length() - t9Digits.length(); i++) {
            boolean match = true;
            for (int j = 0; j < t9Digits.length(); j++) {
                if (mapCharToT9(name.charAt(i + j)) != t9Digits.charAt(j)) {
                    match = false;
                    break;
                }
            }
            if (match) return true;
        }
        return false;
    }

    private void updateSearch() {
        long now = System.currentTimeMillis();
        // Reset if too much time passed
        if (now - lastT9KeyTime > T9_TIMEOUT && t9Query.length() > 0) {
            t9Query.setLength(0);
        }
        lastT9KeyTime = now;

        String digits = t9Query.toString();

        installedApps.clear();
        if (digits.isEmpty()) {
            installedApps.addAll(fullAppList);
            searchBar.setVisibility(View.GONE);
        } else {
            searchBar.setVisibility(View.VISIBLE);
            searchBar.setText("Search: " + digits);
            
            for (AppInfo app : fullAppList) {
                if (matchesT9(app.name, digits)) {
                    installedApps.add(app);
                }
            }
        }
        
        rebuildUI();
        
        // If we have results, focus the first one
        if (!installedApps.isEmpty()) {
            setSelection(0);
            // Manually trigger focus animation for first item
             post(new Runnable() {
                 @Override
                 public void run() {
                     if (getChildCount() > 0) {
                         getChildAt(0).requestFocus();
                     }
                 }
             });
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Check for Number keys (0-9)
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            char digit = (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
            
            // If it's a new session (timeout passed), clear previous query
            if (System.currentTimeMillis() - lastT9KeyTime > T9_TIMEOUT) {
                t9Query.setLength(0);
            }
            
            t9Query.append(digit);
            updateSearch();
            return true;
        }
        
        // Backspace / Delete support
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (t9Query.length() > 0) {
                t9Query.deleteCharAt(t9Query.length() - 1);
                // Force timestamp update to keep session alive during deletion
                lastT9KeyTime = System.currentTimeMillis(); 
                updateSearch();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int sel = getSelectedItemPosition();
            if (sel >= 0 && sel < installedApps.size()) {
                getContext().startActivity(installedApps.get(sel).intent);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // If search is active, clear it first
            if (t9Query.length() > 0) {
                t9Query.setLength(0);
                updateSearch();
                return true;
            }
            parent.switchToHome();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }
}
