package com.joyous.app;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.graphics.Color;
import android.view.View;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/** Stable theme names and launcher components must be retained across app updates. */
public final class ThemeManager {
  static final String[] THEMES={"original","dark","light","matrix","kumo","glorp"};
  static final String[] NAMES={"Original","Dark","Light","Matrix","Kumo","Glorp"};
  private static final int[] STYLES={R.style.JoyousOriginal,R.style.JoyousDark,R.style.JoyousLight,R.style.JoyousMatrix,R.style.JoyousKumo,R.style.JoyousGlorp};
  private static final int[] MASCOTS={R.drawable.mascot_original,R.drawable.mascot_dark,R.drawable.mascot_light,R.drawable.mascot_matrix,R.drawable.mascot_kumo,R.drawable.mascot_glorp};
  private static final String[] BACKGROUNDS={"#17131e","#10131a","#f4f2f8","#08090a","#fff0f7","#15291b"};
  private static WeakReference<MainActivity> running=new WeakReference<>(null);
  private static String lastApplied;
  static int index(String theme){for(int i=0;i<THEMES.length;i++)if(THEMES[i].equals(theme))return i;return 0;}
  static String parse(String json){try{return THEMES[index(new JSONObject(json).optString("theme","original"))];}catch(Exception ignored){return "original";}}
  static String saved(Context c){return parse(c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE).getString("prefs","{}"));}
  static int style(String theme){return STYLES[index(theme)];}
  static int mascot(String theme){return MASCOTS[index(theme)];}
  static int background(String theme){return Color.parseColor(BACKGROUNDS[index(theme)]);}
  static void register(MainActivity a){running=new WeakReference<>(a);}
  static void forget(MainActivity a){if(running.get()==a)running.clear();}
  static boolean reuse(MainActivity incoming){
    MainActivity current=running.get();
    if(current==null||current==incoming||current.isFinishing()||current.isDestroyed())return false;
    // Reuse the existing WebView when the launcher component changed while Joyous was open.
    Intent bring=new Intent(incoming,current.getClass()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP);
    if(incoming.getIntent().getBooleanExtra("openAgenda",false))bring.putExtra("openAgenda",true);
    incoming.startActivity(bring);incoming.finish();return true;
  }
  static void ui(MainActivity a,String theme){
    a.setTheme(style(theme));
    int background=background(theme);a.getWindow().setStatusBarColor(background);a.getWindow().setNavigationBarColor(background);
    int flags=a.getWindow().getDecorView().getSystemUiVisibility();
    int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    flags&=~mask;if(theme.equals("light")||theme.equals("kumo"))flags|=mask;
    a.getWindow().getDecorView().setSystemUiVisibility(flags);
    a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
    if(Build.VERSION.SDK_INT>=31)a.getSplashScreen().setSplashScreenTheme(style(theme));
    a.updateNativeBackground(background);
  }
  static void sync(MainActivity a,String prefs){
    String theme=parse(prefs);
    if(theme.equals(lastApplied))return;
    ui(a,theme);
    PackageManager pm=a.getPackageManager();int chosen=index(theme);
    List<PackageManager.ComponentEnabledSetting> batch=null;
    if(Build.VERSION.SDK_INT>=33)batch=new ArrayList<>();
    // Enable first on older Android versions so no moment exists without a launcher icon.
    for(int n=0;n<THEMES.length;n++){
      int i=(chosen+n)%THEMES.length;
      ComponentName component=new ComponentName(a,a.getPackageName()+".Launcher"+NAMES[i]);
      int desired=i==chosen?PackageManager.COMPONENT_ENABLED_STATE_ENABLED:PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
      if(pm.getComponentEnabledSetting(component)==desired)continue;
      if(Build.VERSION.SDK_INT>=33)batch.add(new PackageManager.ComponentEnabledSetting(component,desired,PackageManager.DONT_KILL_APP));
      else pm.setComponentEnabledSetting(component,desired,PackageManager.DONT_KILL_APP);
    }
    if(Build.VERSION.SDK_INT>=33&&!batch.isEmpty())pm.setComponentEnabledSettings(batch);
    lastApplied=theme;
  }
}
