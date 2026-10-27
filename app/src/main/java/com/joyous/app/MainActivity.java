package com.joyous.app;

import android.app.Activity;
import android.os.Build;
import android.Manifest;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.ValueCallback;
import android.view.View;
import android.graphics.Color;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
  private static final int PICK_JSON = 101;
  private static final int SAVE_JSON = 102;
  private WebView web;
  private ValueCallback<Uri[]> fileCallback;
  private String pendingExport;
  private android.widget.FrameLayout root;
  private android.widget.ImageView loadingMascot;

  @Override public void onCreate(Bundle state) {
    String launchTheme=ThemeManager.saved(this);
    setTheme(ThemeManager.style(launchTheme));
    super.onCreate(state);
    if(ThemeManager.reuse(this))return;
    ThemeManager.register(this);
    ReminderReceiver.channel(this);
    ReminderReceiver.schedule(this);
    AgendaReminders.schedule(this);
    if (Build.VERSION.SDK_INT >= 33 &&
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 300);
    }
    web = new WebView(this);
    web.setBackgroundColor(ThemeManager.background(launchTheme));
    root=new android.widget.FrameLayout(this);
    root.setBackgroundColor(ThemeManager.background(launchTheme));
    root.addView(web,new android.widget.FrameLayout.LayoutParams(-1,-1));
    loadingMascot=new android.widget.ImageView(this);
    loadingMascot.setBackgroundColor(ThemeManager.background(launchTheme));
    android.graphics.drawable.Drawable mascot=getDrawable(ThemeManager.mascot(launchTheme));
    int mascotSize=(int)(180*getResources().getDisplayMetrics().density);
    mascot.setBounds(0,0,mascotSize,mascotSize);
    loadingMascot.setImageDrawable(mascot);
    loadingMascot.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
    int padding=(int)(getResources().getDisplayMetrics().density*64);
    loadingMascot.setPadding(padding,padding,padding,padding);
    loadingMascot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    root.addView(loadingMascot,new android.widget.FrameLayout.LayoutParams(-1,-1));
    setContentView(root);
    ThemeManager.ui(this,launchTheme);
    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setAllowFileAccess(false);
    settings.setAllowContentAccess(true);
    settings.setMediaPlaybackRequiresUserGesture(false);
    web.addJavascriptInterface(new BackupBridge(), "JoyousNative");
    web.setWebChromeClient(new WebChromeClient() {
      @Override public boolean onJsConfirm(WebView view,String url,String message,android.webkit.JsResult result){
        new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message)
          .setPositiveButton(android.R.string.ok,(dialog,which)->result.confirm())
          .setNegativeButton(android.R.string.cancel,(dialog,which)->result.cancel())
          .setOnCancelListener(dialog->result.cancel()).show();return true;
      }
      @Override public boolean onJsAlert(WebView view,String url,String message,android.webkit.JsResult result){
        new android.app.AlertDialog.Builder(MainActivity.this).setMessage(message)
          .setPositiveButton(android.R.string.ok,(dialog,which)->result.confirm())
          .setOnCancelListener(dialog->result.confirm()).show();return true;
      }
      @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (fileCallback != null) fileCallback.onReceiveValue(null);
        fileCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"});
        startActivityForResult(intent, PICK_JSON);
        return true;
      }
    });
    web.setWebViewClient(new WebViewClient() {
      @Override public void onPageFinished(WebView view, String url) {
        view.postVisualStateCallback(1,new WebView.VisualStateCallback(){
          @Override public void onComplete(long requestId){if(loadingMascot!=null){root.removeView(loadingMascot);loadingMascot=null;}}
        });
        view.evaluateJavascript("(function(){function sync(){try{JoyousNative.syncReminders(localStorage.getItem('diacheck_v1')||'{}',localStorage.getItem('joyoustrack_preferences_v2')||'{}',localStorage.getItem('joyous_agenda_v1')||'[]',localStorage.getItem('joyous_notifications_v1')||'{}')}catch(e){}};sync();window.addEventListener('storage',sync);var a=Storage.prototype.setItem;Storage.prototype.setItem=function(k,v){a.call(this,k,v);if(k==='diacheck_v1'||k==='joyoustrack_preferences_v2'||k==='joyous_agenda_v1'||k==='joyous_notifications_v1')sync()}})()",null);
        if(getIntent().getBooleanExtra("openAgenda",false)) {
          view.evaluateJavascript("document.querySelector('[data-tab=agenda]')?.click()",null);
          getIntent().removeExtra("openAgenda");
        }
        // Native-only reminder controls are mounted by the shared HTML when the bridge exists.
        // Backup export uses JoyousBackup in the HTML and the Android document picker.
      }
    });
    // HTTPS-like appassets origin gives stable, private localStorage across launches.
    // No internet permission is requested; all application content is bundled.
    web.loadDataWithBaseURL("https://joyous.local/", readAsset(), "text/html", "UTF-8", null);
  }
  private String readAsset() {
    try (java.io.InputStream in = getAssets().open("index.html")) {
      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      byte[] buf = new byte[8192]; int n;
      while ((n=in.read(buf))!=-1) out.write(buf,0,n);
      return out.toString("UTF-8");
    } catch (Exception e) { return "<h1>Joyous</h1><p>Error al cargar la aplicación.</p>"; }
  }
  private class BackupBridge {
    @JavascriptInterface public void syncReminders(String tasks, String prefs, String agenda, String config) {
      boolean configChanged=!config.equals(getSharedPreferences("joyous_reminders", MODE_PRIVATE).getString("notification_config","{}"));
      getSharedPreferences("joyous_reminders", MODE_PRIVATE).edit()
        .putString("tasks",tasks).putString("prefs",prefs).putString("agenda",agenda).putString("notification_config",config).apply();
      runOnUiThread(() -> {
        try { ThemeManager.sync(MainActivity.this,prefs); }
        catch(RuntimeException error){android.util.Log.e("Joyous","Could not update native theme",error);}
      });
      if(configChanged)ReminderReceiver.schedule(MainActivity.this);
      AgendaReminders.schedule(MainActivity.this);
      try { if(ReminderReceiver.pending(MainActivity.this)==0) {
        android.app.NotificationManager nm=(android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        for(int i=0;i<3;i++)nm.cancel(400+i);
      }}catch(Exception ignored){}
    }

    @JavascriptInterface public void notificationSettings() {
      runOnUiThread(() -> {
        Intent intent=new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE,getPackageName());
        startActivity(intent);
      });
    }
    @JavascriptInterface public void exportBackup(String json) {
      runOnUiThread(() -> {
        pendingExport = json;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "Joyous_copia_completa.json");
        startActivityForResult(intent, SAVE_JSON);
      });
    }
  }
  @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_JSON) {
      if (fileCallback != null) {
        fileCallback.onReceiveValue(resultCode == RESULT_OK && data != null ? new Uri[]{data.getData()} : null);
        fileCallback = null;
      }
    } else if (requestCode == SAVE_JSON && resultCode == RESULT_OK && data != null && pendingExport != null) {
      try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
        if (out != null) out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) { android.widget.Toast.makeText(this,"No se pudo guardar la copia",android.widget.Toast.LENGTH_LONG).show(); }
      pendingExport = null;
    }
  }
  void updateNativeBackground(int color){
    if(web!=null)web.setBackgroundColor(color);
    if(root!=null)root.setBackgroundColor(color);
  }
  @Override protected void onNewIntent(Intent intent){
    super.onNewIntent(intent);setIntent(intent);
    ThemeManager.ui(this,ThemeManager.saved(this));
    if(web!=null&&intent.getBooleanExtra("openAgenda",false)){
      web.evaluateJavascript("document.querySelector('[data-tab=agenda]')?.click()",null);
      intent.removeExtra("openAgenda");
    }
  }
  @Override protected void onDestroy(){ThemeManager.forget(this);super.onDestroy();}
  @Override public void onBackPressed() {
    if (web.canGoBack()) web.goBack(); else super.onBackPressed();
  }
}
