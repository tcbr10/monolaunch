package com.monobogdan.monolaunch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SectionIndexer;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppList extends GridView {

    // --- Data Classes ---

    static class AppInfo {
        String name;
        String packageName;
        Intent intent;
        boolean isPinned;
    }

    static class ViewHolder {
        ImageView icon;
        TextView label;
    }

    class PackageManagerListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            reloadApps();
        }
    }

    // --- Fields ---

    private Launcher parent;
    private AppAdapter adapter;
    private ExecutorService backgroundExecutor;
    private Handler mainHandler;
    private LruCache<String, Bitmap> iconCache;

    // Data (Volatile ensures thread safety for simple swaps)
    private volatile List<AppInfo> installedApps = new ArrayList<>();
    private List<AppInfo> visibleApps = new ArrayList<>();

    // State
    private SharedPreferences prefs;
    private Set<String> pinnedPackages;
    private Set<String> hiddenPackages;
    private static final String PREF_NAME = "launcher_prefs";
    private static final String KEY_PINNED = "pinned_apps";
    private static final String KEY_HIDDEN = "hidden_apps";

    // UI
    private Drawable focusBackground;
    private TextView searchBar;
    private boolean isSearchAttached = false;
    private View lastSelectedView = null;

    // T9 State
    private StringBuilder t9Query = new StringBuilder();
    private boolean isSearching = false;
    
    // T9 Maps
    private static final Map<Character, Character> T9_MAP = new HashMap<>();
    static {
        // English
        mapT9("abcABC", '2'); mapT9("defDEF", '3'); mapT9("ghiGHI", '4');
        mapT9("jklJKL", '5'); mapT9("mnoMNO", '6'); mapT9("pqrsPQRS", '7');
        mapT9("tuvTUV", '8'); mapT9("wxyzWXYZ", '9');
        // Hebrew
        mapT9("אבג", '2'); mapT9("דהו", '3'); mapT9("זחט", '4');
        mapT9("יכלך", '5'); mapT9("מנסםן", '6'); mapT9("עפצףץ", '7');
        mapT9("קרש", '8'); mapT9("ת", '9');
    }
    private static void mapT9(String letters, char digit) {
        for (char c : letters.toCharArray()) T9_MAP.put(c, digit);
    }

    // --- Constructors ---

    public AppList(Context context) {
        super(context);
        init(context);
    }

    public AppList(Launcher launcher) {
        super(launcher);
        this.parent = launcher;
        init(launcher);
    }

    private void init(Context context) {
        // Threading & Cache
        backgroundExecutor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
        int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);
        iconCache = new LruCache<String, Bitmap>(maxMemory / 8) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };

        // Preferences
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        pinnedPackages = new HashSet<>(prefs.getStringSet(KEY_PINNED, new HashSet<String>()));
        hiddenPackages = new HashSet<>(prefs.getStringSet(KEY_HIDDEN, new HashSet<String>()));

        initFocusDrawable();
        initSearchBar(context);

        // Grid Settings
        setNumColumns(3);
        setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        setSelector(new ColorDrawable(Color.TRANSPARENT)); // We handle focus manually
        setFastScrollEnabled(true);
        
        // Adapter
        adapter = new AppAdapter();
        setAdapter(adapter);

        // --- Selection & Focus ---
        setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 1. Reset previous view
                if (lastSelectedView != null && lastSelectedView != view) {
                    lastSelectedView.setBackground(null);
                    lastSelectedView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150);
                }
                // 2. Highlight new view
                if (view != null) {
                    view.setBackground(focusBackground);
                    view.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100);
                    lastSelectedView = view;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (lastSelectedView != null) {
                    lastSelectedView.setBackground(null);
                    lastSelectedView.animate().scaleX(1.0f).scaleY(1.0f).start();
                    lastSelectedView = null;
                }
            }
        });

        // Click Listeners
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < visibleApps.size()) {
                    safeStartActivity(visibleApps.get(position).intent);
                }
            }
        });

        setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                showAppOptions(position);
                return true; // Consumed
            }
        });

        // Receivers
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        context.registerReceiver(new PackageManagerListener(), filter);

        reloadApps();
    }

    private void initFocusDrawable() {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.TRANSPARENT);
        gd.setCornerRadius(8 * density);
        gd.setStroke((int)(3 * density), Color.WHITE);
        focusBackground = gd;
    }

    private Activity getActivity() {
        Context context = getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity)context;
            }
            context = ((ContextWrapper)context).getBaseContext();
        }
        return null;
    }

    private void initSearchBar(Context context) {
        searchBar = new TextView(context);
        searchBar.setBackgroundColor(0xEE222222);
        searchBar.setTextColor(Color.YELLOW);
        searchBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        searchBar.setGravity(Gravity.CENTER);
        int p = (int)(12 * getResources().getDisplayMetrics().density);
        searchBar.setPadding(p, p, p, p);
        searchBar.setVisibility(View.GONE);
    }

    private void safeStartActivity(Intent intent) {
        try {
            if (intent != null) getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot start app", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Data Loading (Thread Safe) ---

    private void reloadApps() {
        backgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                // 1. Build NEW list (don't modify existing one to avoid concurrent exception)
                List<AppInfo> newList = new ArrayList<>();
                
                Intent filter = new Intent(Intent.ACTION_MAIN);
                filter.addCategory(Intent.CATEGORY_LAUNCHER);
                PackageManager pm = getContext().getPackageManager();

                List<ResolveInfo> rawApps;
                if (Build.VERSION.SDK_INT >= 33) {
                    rawApps = pm.queryIntentActivities(filter, PackageManager.ResolveInfoFlags.of(0));
                } else {
                    rawApps = pm.queryIntentActivities(filter, 0);
                }

                for (ResolveInfo info : rawApps) {
                    String pkg = info.activityInfo.packageName;
                    if (hiddenPackages.contains(pkg)) continue;

                    AppInfo app = new AppInfo();
                    app.name = info.loadLabel(pm).toString();
                    app.packageName = pkg;
                    app.intent = pm.getLaunchIntentForPackage(pkg);
                    app.isPinned = pinnedPackages.contains(pkg);

                    // Cache icon
                    if (iconCache.get(pkg) == null) {
                        try {
                            Drawable d = info.loadIcon(pm);
                            Bitmap b = drawableToBitmap(d);
                            if (b != null) iconCache.put(pkg, b);
                        } catch (OutOfMemoryError e) {
                            iconCache.evictAll(); 
                        }
                    }
                    
                    if (app.intent != null) newList.add(app);
                }

                Collections.sort(newList, new Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo a, AppInfo b) {
                        if (a.isPinned != b.isPinned) return a.isPinned ? -1 : 1;
                        return a.name.compareToIgnoreCase(b.name);
                    }
                });

                // 2. Swap on Main Thread
                mainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        installedApps = newList; // Atomic swap
                        if (isSearching) {
                            applyT9Filter(); // Re-filter with new data
                        } else {
                            visibleApps = new ArrayList<>(installedApps);
                            adapter.notifyDataSetChanged();
                        }
                    }
                });
            }
        });
    }

    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable instanceof BitmapDrawable) return ((BitmapDrawable) drawable).getBitmap();
        int w = drawable.getIntrinsicWidth();
        int h = drawable.getIntrinsicHeight();
        if (w <= 0) w = 48; if (h <= 0) h = 48;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        drawable.setBounds(0, 0, c.getWidth(), c.getHeight());
        drawable.draw(c);
        return b;
    }

    // --- Adapter ---

    private class AppAdapter extends BaseAdapter implements SectionIndexer {
        
        private HashMap<String, Integer> alphaIndexer;
        private String[] sections;

        @Override
        public void notifyDataSetChanged() {
            alphaIndexer = new HashMap<>();
            for (int i = 0; i < visibleApps.size(); i++) {
                if (visibleApps.get(i).name.length() > 0) {
                    String s = visibleApps.get(i).name.substring(0, 1).toUpperCase();
                    if (!alphaIndexer.containsKey(s)) alphaIndexer.put(s, i);
                }
            }
            ArrayList<String> sectionList = new ArrayList<>(alphaIndexer.keySet());
            Collections.sort(sectionList);
            sections = new String[sectionList.size()];
            sectionList.toArray(sections);
            super.notifyDataSetChanged();
        }

        @Override
        public int getCount() { return visibleApps.size(); }
        @Override
        public AppInfo getItem(int position) { return visibleApps.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                float density = getResources().getDisplayMetrics().density;
                int iconSize = (int)(56 * density);
                
                LinearLayout container = new LinearLayout(getContext());
                container.setOrientation(LinearLayout.VERTICAL);
                container.setGravity(Gravity.CENTER);
                int pad = (int)(8 * density);
                container.setPadding(pad, pad, pad, pad);
                
                // CRITICAL: These must be FALSE so GridView handles the events
                container.setFocusable(false);
                container.setClickable(false); 
                
                if (Build.VERSION.SDK_INT >= 17) {
                    container.setLayoutDirection(View.LAYOUT_DIRECTION_LOCALE);
                }

                ImageView iv = new ImageView(getContext());
                iv.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));

                TextView tv = new TextView(getContext());
                tv.setSingleLine(true);
                tv.setEllipsize(TextUtils.TruncateAt.END);
                tv.setTextColor(Color.WHITE);
                tv.setTextSize(12);
                tv.setGravity(Gravity.CENTER);

                container.addView(iv);
                container.addView(tv);

                holder = new ViewHolder();
                holder.icon = iv;
                holder.label = tv;
                container.setTag(holder);
                convertView = container;
            } else {
                holder = (ViewHolder) convertView.getTag();
                convertView.setBackground(null); // Reset recycled view state
                convertView.setScaleX(1.0f);
                convertView.setScaleY(1.0f);
            }

            AppInfo app = getItem(position);
            holder.label.setText(app.name);
            holder.label.setTextColor(app.isPinned ? Color.YELLOW : Color.WHITE);

            Bitmap b = iconCache.get(app.packageName);
            if (b != null) holder.icon.setImageBitmap(b);
            else holder.icon.setImageResource(android.R.drawable.sym_def_app_icon);

            return convertView;
        }

        @Override
        public Object[] getSections() { return sections; }
        @Override
        public int getPositionForSection(int sectionIndex) {
            if(sections == null || sections.length == 0) return 0;
            return alphaIndexer.get(sections[Math.min(sectionIndex, sections.length - 1)]);
        }
        @Override
        public int getSectionForPosition(int position) { return 0; }
    }

    // --- Menu & T9 Logic ---

    private void showAppOptions(int position) {
        if (position < 0 || position >= visibleApps.size()) return;
        
        final AppInfo app = visibleApps.get(position);
        
        List<String> ops = new ArrayList<>();
        ops.add(app.isPinned ? "Unpin App" : "Pin App");
        ops.add("Uninstall");
        ops.add("Hide App");
        ops.add("System Settings");
        if (!hiddenPackages.isEmpty()) ops.add("Reset Hidden Apps");

        final String[] options = ops.toArray(new String[0]);

        new AlertDialog.Builder(getContext())
            .setTitle(app.name)
            .setItems(options, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String sel = options[which];
                    if (sel.contains("Pin")) togglePin(app);
                    else if (sel.equals("Uninstall")) {
                        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.packageName));
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getContext().startActivity(i);
                    }
                    else if (sel.equals("Hide App")) {
                        hiddenPackages.add(app.packageName);
                        prefs.edit().putStringSet(KEY_HIDDEN, hiddenPackages).apply();
                        reloadApps();
                    }
                    else if (sel.equals("System Settings")) 
                        safeStartActivity(new Intent(Settings.ACTION_SETTINGS));
                    else if (sel.equals("Reset Hidden Apps")) {
                        hiddenPackages.clear();
                        prefs.edit().remove(KEY_HIDDEN).apply();
                        reloadApps();
                    }
                }
            })
            .show();
    }

    private void togglePin(AppInfo app) {
        if (pinnedPackages.contains(app.packageName)) pinnedPackages.remove(app.packageName);
        else pinnedPackages.add(app.packageName);
        prefs.edit().putStringSet(KEY_PINNED, pinnedPackages).apply();
        reloadApps();
    }

    private String getT9Signature(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (T9_MAP.containsKey(c)) sb.append(T9_MAP.get(c));
            else if (Character.isDigit(c)) sb.append(c);
        }
        return sb.toString();
    }

    private void applyT9Filter() {
        String query = t9Query.toString();
        List<AppInfo> tempResult = new ArrayList<>();
        
        // Iterate over local 'installedApps' to avoid CME
        List<AppInfo> source = installedApps; 
        
        if (query.isEmpty()) {
            isSearching = false;
            visibleApps = new ArrayList<>(source);
            toggleSearchBar(false);
        } else {
            isSearching = true;
            for (AppInfo app : source) {
                if (getT9Signature(app.name).contains(query)) tempResult.add(app);
            }
            visibleApps = tempResult;
            toggleSearchBar(true);
        }
        adapter.notifyDataSetChanged();
        if (!visibleApps.isEmpty()) setSelection(0);
    }

    private void toggleSearchBar(boolean show) {
        Activity activity = getActivity();
        if (activity == null || searchBar == null) return;

        if (show) {
            if (!isSearchAttached) {
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                lp.gravity = Gravity.TOP;
                activity.addContentView(searchBar, lp);
                isSearchAttached = true;
            }
            searchBar.setVisibility(View.VISIBLE);
            searchBar.setText("Search: " + t9Query.toString());
            searchBar.bringToFront();
        } else {
            searchBar.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // T9
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            t9Query.append((char)('0' + (keyCode - KeyEvent.KEYCODE_0)));
            applyT9Filter();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (t9Query.length() > 0) {
                t9Query.deleteCharAt(t9Query.length() - 1);
                applyT9Filter();
            } else if (isSearching) {
                t9Query.setLength(0);
                applyT9Filter();
            }
            return true;
        }
        // Left Menu Button Check
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SOFT_LEFT) {
            int pos = getSelectedItemPosition();
            if (pos != AdapterView.INVALID_POSITION) {
                showAppOptions(pos);
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            int pos = getSelectedItemPosition();
            if (pos != AdapterView.INVALID_POSITION && pos < visibleApps.size()) {
                safeStartActivity(visibleApps.get(pos).intent);
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (isSearching) {
                t9Query.setLength(0);
                applyT9Filter();
                return true;
            }
            if (parent != null) parent.switchToHome();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isSearchAttached && searchBar != null && searchBar.getParent() != null) {
            ((ViewGroup) searchBar.getParent()).removeView(searchBar);
        }
        if (backgroundExecutor != null) backgroundExecutor.shutdown();
    }
}
