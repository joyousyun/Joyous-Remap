package com.joyous.app;

import android.app.*;
import android.content.*;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.*;
import java.time.*;
import java.util.*;

/** Keeps one alarm for the next agenda notification; persisted delivery IDs prevent repeats. */
public final class AgendaReminders {
  private static final String CHANNEL="joyous_agenda";
  private static android.content.SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("joyous_reminders",Context.MODE_PRIVATE);
  }
  private static PendingIntent alarm(Context c) {
    return PendingIntent.getBroadcast(c,600,new Intent(c,ReminderReceiver.class).setAction("agenda"),
        PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  }
  static class Occurrence {
    JSONObject entry; String date,key; long time;
    Occurrence(JSONObject e, LocalDate d) {
      entry=e; date=d.toString(); key=e.optString("id")+"|"+date+"|"+e.optString("time")+"|"+e.optString("alert");
      LocalTime clock=LocalTime.parse(e.optString("time","").isEmpty()?"10:00":e.optString("time"));
      LocalDateTime at=d.atTime(clock);
      String alert=e.optString("alert");
      if(alert.equals("day"))at=at.minusDays(1);
      if(alert.equals("week"))at=at.minusDays(7);
      time=at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
  }
  private static List<Occurrence> occurrences(Context c) {
    List<Occurrence> result=new ArrayList<>();
    try {
      JSONArray entries=new JSONArray(prefs(c).getString("agenda","[]"));
      int year=LocalDate.now().getYear();
      for(int i=0;i<entries.length();i++) {
        try {
          JSONObject e=entries.getJSONObject(i); String alert=e.optString("alert","none");
          if(!Arrays.asList("at","day","week").contains(alert)||e.optString("id").isEmpty())continue;
          LocalDate original=LocalDate.parse(e.getString("date"));
          if(e.optString("repeat").equals("annual")) {
            for(int y=Math.max(original.getYear(),year-1);y<=year+2;y++) {
              LocalDate date=original.withYear(y); // Feb 29 becomes Feb 28 in a non-leap year, matching HTML.
              result.add(new Occurrence(e,date));
            }
          } else result.add(new Occurrence(e,original));
        } catch(Exception invalidEntry) { /* One malformed entry must not suppress other reminders. */ }
      }
    }catch(Exception ignored){}
    return result;
  }
  public static synchronized void schedule(Context c) {
    AlarmManager mgr=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
    mgr.cancel(alarm(c)); if(!ReminderReceiver.agendaEnabled(c)){prefs(c).edit().remove("agenda_registered").apply();return;} long now=System.currentTimeMillis(),next=Long.MAX_VALUE;
    Set<String> sent=prefs(c).getStringSet("agenda_sent",Collections.emptySet());
    Set<String> registered=prefs(c).getStringSet("agenda_registered",Collections.emptySet());
    Set<String> eligible=new HashSet<>();
    for(Occurrence o:occurrences(c)) {
      if(sent.contains(o.key))continue;
      boolean future=o.time>now;
      boolean overdue=registered.contains(o.key)&&o.time>=now-86400000L;
      if(!future&&!overdue)continue;
      eligible.add(o.key);
      next=Math.min(next,future?o.time:now+1000);
    }
    prefs(c).edit().putStringSet("agenda_registered",eligible).apply();
    if(next!=Long.MAX_VALUE)mgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,alarm(c));
  }
  public static synchronized void deliver(Context c) {
    if(!ReminderReceiver.agendaEnabled(c)){schedule(c);return;}
    long now=System.currentTimeMillis();
    Set<String> sent=new HashSet<>(prefs(c).getStringSet("agenda_sent",Collections.emptySet()));
    boolean allowed=Build.VERSION.SDK_INT<33||c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED;
    NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
    NotificationChannel ch=new NotificationChannel(CHANNEL,"Agenda",NotificationManager.IMPORTANCE_DEFAULT);
    nm.createNotificationChannel(ch);
    boolean en=false;
    try{en=new JSONObject(prefs(c).getString("prefs","{}")).optString("language").equals("en");}catch(Exception ignored){}
    Set<String> registered=prefs(c).getStringSet("agenda_registered",Collections.emptySet());
    Set<String> keep=new HashSet<>();
    for(Occurrence o:occurrences(c)) {
      // Don't resurrect old reminders; tolerate delayed delivery for up to 24 hours.
      if(o.time>=now-86400000L)keep.add(o.key);
      if(o.time>now||o.time<now-86400000L||sent.contains(o.key)||!registered.contains(o.key))continue;
      if(allowed) {
        Intent open=new Intent(c,MainActivity.class).putExtra("openAgenda",true);
        PendingIntent tap=PendingIntent.getActivity(c,601,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        String body=o.date+" · "+(o.entry.optString("time").isEmpty()?(en?"All day":"Todo el día"):o.entry.optString("time"));
        String note=o.entry.optString("note"); if(!note.isEmpty())body+="\n"+note;
        Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_popup_reminder)
          .setContentTitle((en?"Joyous · Calendar: ":"Joyous · Agenda: ")+o.entry.optString("title"))
          .setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
          .setContentIntent(tap).setAutoCancel(true).build();
        nm.notify(o.key,602,n);
      }
      sent.add(o.key);
    }
    sent.retainAll(keep); prefs(c).edit().putStringSet("agenda_sent",sent).apply();
    schedule(c);
  }
}
