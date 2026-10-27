package com.joyous.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.util.Calendar;

public class ReminderReceiver extends BroadcastReceiver {
  public static final String CHANNEL = "joyous_pending";
  private static final int[] HOURS = {10,16,21};

  public static void channel(Context c) {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel ch = new NotificationChannel(CHANNEL,"Hábitos pendientes",
          NotificationManager.IMPORTANCE_DEFAULT);
      ch.setDescription("Avisos de tareas pendientes con la personalidad de tu mascota");
      ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE))
          .createNotificationChannel(ch);
    }
  }
  static JSONObject config(Context c){try{return new JSONObject(c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE).getString("notification_config","{}"));}catch(Exception ignored){return new JSONObject();}}
  static boolean agendaEnabled(Context c){return config(c).optBoolean("agendaEnabled",true);}
  static JSONObject slotConfig(Context c,int slot){JSONArray slots=config(c).optJSONArray("slots");JSONObject value=slots==null?null:slots.optJSONObject(slot);return value==null?new JSONObject():value;}
  public static void schedule(Context c) {
    AlarmManager mgr=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    for(int i=0;i<3;i++){
      Intent intent=new Intent(c,ReminderReceiver.class).putExtra("slot",i);
      PendingIntent pi=PendingIntent.getBroadcast(c,300+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      mgr.cancel(pi);JSONObject slot=slotConfig(c,i);if(!slot.optBoolean("enabled",true)){((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(400+i);continue;}
      String time=slot.optString("time",String.format(java.util.Locale.US,"%02d:00",HOURS[i]));
      if(!time.matches("([01]\\d|2[0-3]):[0-5]\\d"))time=String.format(java.util.Locale.US,"%02d:00",HOURS[i]);
      intent.putExtra("configuredTime",time);pi=PendingIntent.getBroadcast(c,300+i,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
      String[] parts=time.split(":");Calendar at=Calendar.getInstance();at.set(Calendar.HOUR_OF_DAY,Integer.parseInt(parts[0]));at.set(Calendar.MINUTE,Integer.parseInt(parts[1]));at.set(Calendar.SECOND,0);at.set(Calendar.MILLISECOND,0);
      if(at.getTimeInMillis()<=System.currentTimeMillis())at.add(Calendar.DAY_OF_YEAR,1);
      mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at.getTimeInMillis(),pi);
    }
  }
  static int pending(Context c) {
    try {
      String raw=c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE).getString("tasks","{}");
      JSONObject data=new JSONObject(raw);
      JSONArray tasks=data.optJSONArray("tasks");
      if(tasks==null)return 0;
      JSONObject marks=data.optJSONObject("marks");
      Calendar now=Calendar.getInstance();
      String today=String.format(java.util.Locale.US,"%04d-%02d-%02d",
          now.get(Calendar.YEAR),now.get(Calendar.MONTH)+1,now.get(Calendar.DAY_OF_MONTH));
      int n=0;
      for(int i=0;i<tasks.length();i++){JSONObject task=tasks.optJSONObject(i);if(task!=null&&NativeTaskRules.pending(task,marks,today))n++;}
      return n;
    }catch(Exception e){return 0;}
  }
  private static String phrase(Context c,String theme,int slot,boolean en) {
    try (java.io.InputStream in=c.getAssets().open("notification_phrases.json")) {
      java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
      byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1)out.write(buf,0,n);
      JSONObject themes=new JSONObject(out.toString("UTF-8")).getJSONObject(en?"en":"es");
      if(!themes.has(theme))theme="original";
      JSONArray choices=themes.getJSONArray(theme).getJSONArray(Math.max(0,Math.min(2,slot)));
      return choices.getString(new java.util.Random().nextInt(choices.length()));
    } catch(Exception e) { return en?"You have pending tasks.":"Tienes tareas pendientes."; }
  }
  @Override public void onReceive(Context c,Intent intent) {
    if(intent!=null&&"agenda".equals(intent.getAction())) { AgendaReminders.deliver(c); return; }
    schedule(c);
    int requestedSlot=intent==null?0:intent.getIntExtra("slot",0);
    if(requestedSlot<0||requestedSlot>2||!slotConfig(c,requestedSlot).optBoolean("enabled",true))return;
    String configured=intent==null?null:intent.getStringExtra("configuredTime");
    if(configured!=null&&!configured.equals(slotConfig(c,requestedSlot).optString("time",String.format(java.util.Locale.US,"%02d:00",HOURS[requestedSlot]))))return;
    int count=pending(c);
    if(count==0){ NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE); for(int i=0;i<3;i++)nm.cancel(400+i); return; }
    if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        !=PackageManager.PERMISSION_GRANTED)return;
    channel(c);
    JSONObject prefs;
    try{prefs=new JSONObject(c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE)
        .getString("prefs","{}"));}catch(Exception e){prefs=new JSONObject();}
    boolean en=prefs.optString("language","es").equals("en");
    int slot=intent==null?0:intent.getIntExtra("slot",0);
    String stamp=java.time.LocalDate.now().toString()+"|"+slot;
    android.content.SharedPreferences delivery=c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE);
    if(stamp.equals(delivery.getString("habit_sent_"+slot,"")))return;
    String title=en?"Joyous · "+count+" pending": "Joyous · "+count+" pendiente"+(count==1?"":"s");
    Intent open=new Intent(c,MainActivity.class);
    PendingIntent tap=PendingIntent.getActivity(c,401,open,
        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification.Builder b=Build.VERSION.SDK_INT>=26?
        new Notification.Builder(c,CHANNEL):new Notification.Builder(c);
    String message=phrase(c,prefs.optString("theme","original"),slot,en);
    b.setSmallIcon(android.R.drawable.ic_popup_reminder).setContentTitle(title)
        .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
        .setAutoCancel(true).setContentIntent(tap);
    ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE))
        .notify(400+slot,b.build());
    delivery.edit().putString("habit_sent_"+slot,stamp).apply();
  }
}
