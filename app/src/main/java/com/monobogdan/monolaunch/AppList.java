package com.monobogdan.monolaunch;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    
    // App lists
    private ArrayList<AppInfo> installedApps; // All apps
    private ArrayList<AppInfo> filteredApps;  // Search results
    private List<AppInfo> visibleApps;        // Currently displayed (either installed or filtered)

    private List<View> widgetList = new ArrayList<>();

    // UI Elements
    private Drawable focusBackground;
    private Drawable normalBackground;
    private TextView searchBar;

    // T9 Search State
    private boolean isSearching = false;
    private StringBuilder t9Query = new StringBuilder();

    // T9 Mappings
    private static final Map<Character, Character> EN_T9 = new HashMap<>();
    private static final Map<Character, Character> HE_T9 = new HashMap<>();

    static {
        // English Standard
        mapT9(EN_T9, "abc", '2');
        mapT9(EN_T9, "def", '3');
        mapT9(EN_T9, "ghi", '4');
        mapT9(EN_T9, "jkl", '5');
        mapT9(EN_T9, "mno", '6');
        mapT9(EN_T9, "pqrs", '7');
        mapT9(EN_T9, "tuv", '8');
        mapT9(EN_T9, "wxyz", '9');

        // Hebrew Standard (Common Keypad Layout)
        mapT9(HE_T9, "אבג", '2');
        mapT9(HE_T9, "דהו", '3');
        mapT9(HE_T9, "זחט", '4');
        mapT9(HE_T9, "יכל", '5');
        mapT9(HE_T9, "מנס", '6');
        mapT9(HE_T9, "עפצ", '7');
        mapT9(HE_T9, "קרש", '8');
        mapT9(HE_T9, "ת", '9');
    }

    private static void mapT9(Map<Character, Character> map, String letters, char digit) {
        for (char c : letters.toCharArray()) {
            map.put(c, digit);
        }
    }

    public AppListView(Launcher launcher) {
        super(launcher.getApplicationContext());

        this.parent = launcher;
        
        // Initialize lists
        installedApps = new ArrayList<>();
        filteredApps = new ArrayList<>();
        visibleApps = installedApps; // Default to showing all

        // Setup UI helpers
        initFocusBackgrounds();
        initSearchBar(launcher);

        // Listeners
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Log.i("", "onItemSelected: " + position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        launcher.getApplicationContext().registerReceiver(new PackageManagerListener(), filter);

        // Load data
        fetchAppList();
        rebuildUI();
    }

    private void initFocusBackgrounds() {
        final float density = getResources().getDisplayMetrics().density;

        // Create a rounded border drawable for focus state
        GradientDrawable focused = new GradientDrawable();
        focused.setColor(Color.TRANSPARENT);
        focused.setCornerRadius(8 * density); // Rounded corners
        focused.setStroke((int) (3 * density), Color.WHITE); // White thick border

        // Transparent for normal state
        ColorDrawable normal = new ColorDrawable(Color.TRANSPARENT);

        focusBackground = focused;
        normalBackground = normal;
    }

    private void initSearchBar(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            
            searchBar = new TextView(context);
            searchBar.setBackgroundColor(0xEE000000); // Dark semi-transparent background
            searchBar.setTextColor(Color.WHITE);
            
            float density = getResources().getDisplayMetrics().density;
            int p = (int)(12 * density);
            searchBar.setPadding(p, p, p, p);
            searchBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            searchBar.setVisibility(View.GONE); // Hidden by default
            
            // Place it at the top of the screen
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            lp.gravity = Gravity.TOP;
            
            activity.addContentView(searchBar, lp);
        }
    }

    private void fetchAppList() {
        installedApps.clear();

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

        // Sort alphabetically
        Collections.sort(installedApps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo a1, AppInfo a2) {
                return a1.name.compareToIgnoreCase(a2.name);
            }
        });

        // If we were searching, re-apply filter to new list, otherwise show all
        if (isSearching) {
            applyT9Filter();
        } else {
            visibleApps = installedApps;
        }
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) return null;

        if (drawable instanceof BitmapDrawable) {
            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
            if (bitmap != null) return bitmap;
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

        // Dynamic sizing
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
        setSelector(new ColorDrawable(Color.TRANSPARENT)); // Disable default orange selector

        widgetList.clear();

        // Animation listener
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view != null) {
                    // Scale up focused item
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100);
                    view.setBackground(focusBackground); // Apply border

                    // Reset others
                    for (View v : widgetList) {
                        if (v != view) {
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100);
                            v.setBackground(normalBackground);
                        }
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Optional: select last item if nothing selected
                if (visibleApps.size() > 0) setSelection(visibleApps.size() - 1);
            }
        });

        // Build Views from visibleApps
        for (int i = 0; i < visibleApps.size(); i++) {
            final AppInfo app = visibleApps.get(i);

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
            container.setBackground(normalBackground); // Default no border

            ImageView iv = new ImageView(getContext());
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

            // Click Handler
            container.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        getContext().startActivity(app.intent);
                    } catch (Exception ex) {
                        Log.e("AppListView", "Failed to launch " + app.name, ex);
                    }
                }
            });

            // Focus Handler (Important for D-pad)
            container.setOnFocusChangeListener(new OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(120);
                        v.setBackground(focusBackground); // SHOW BORDER
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(160);
                        v.setBackground(normalBackground); // HIDE BORDER
                    }
                }
            });

            widgetList.add(container);
        }

        setAdapter(new BaseAdapter() {
            @Override
            public int getCount() { return visibleApps.size(); }
            @Override
            public Object getItem(int position) { return visibleApps.get(position); }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                return widgetList.get(position);
            }
        });
    }

    // --- T9 Logic ---

    private Character mapCharToT9(char ch) {
        char c = Character.toLowerCase(ch);
        if (EN_T9.containsKey(c)) return EN_T9.get(c);
        if (HE_T9.containsKey(c)) return HE_T9.get(c);
        return null; // Symbol or number
    }

    private String nameToT9(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            Character digit = mapCharToT9(c);
            if (digit != null) sb.append(digit);
            // You can optionally append the digit itself if it's a number in the name
            else if (Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private void applyT9Filter() {
        filteredApps.clear();

        if (t9Query.length() == 0) {
            isSearching = false;
            visibleApps = installedApps;
            if (searchBar != null) searchBar.setVisibility(View.GONE);
        } else {
            isSearching = true;
            String query = t9Query.toString();
            
            for (AppInfo app : installedApps) {
                String t9Signature = nameToT9(app.name);
                // Match if T9 signature contains the query sequence
                if (t9Signature.contains(query)) {
                    filteredApps.add(app);
                }
            }
            visibleApps = filteredApps;
            
            if (searchBar != null) {
                searchBar.setVisibility(View.VISIBLE);
                searchBar.setText("Search: " + query);
            }
        }

        rebuildUI();
        // Auto-select first result
        if (visibleApps.size() > 0) {
            setSelection(0);
        }
    }

    // --- Key Event Handling ---

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Capture number keys for T9
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            char digit = (char) ('0' + (keyCode - KeyEvent.KEYCODE_0));
            // Usually 0 is Space, but for app search we might just ignore or treat as '0'
            t9Query.append(digit);
            applyT9Filter();
            return true;
        }

        // Capture Backspace/Del
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (t9Query.length() > 0) {
                t9Query.deleteCharAt(t9Query.length() - 1);
                applyT9Filter();
                return true;
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            int sel = getSelectedItemPosition();
            if (sel >= 0 && sel < visibleApps.size()) {
                getContext().startActivity(visibleApps.get(sel).intent);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // If searching, back clears search first
            if (isSearching) {
                t9Query.setLength(0);
                applyT9Filter(); // will reset to full list
                return true;
            }
            // If not searching, exit to home
            parent.switchToHome();
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        // HACK: Maintain focus on list when returning
        if (visibleApps.size() > 0 && getSelectedItemPosition() == -1)
            setSelection(0);
    }
}
