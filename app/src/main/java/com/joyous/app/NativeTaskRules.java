package com.joyous.app;
import org.json.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Today's eligibility mirrors JoyousTasks in the bundled HTML. */
final class NativeTaskRules {
  static boolean pending(JSONObject task,JSONObject allMarks,String today){
    try {
      String created=task.optString("created");if(created.isEmpty()||created.compareTo(today)>0||task.optBoolean("archived",false))return false;
      JSONObject selected=null;String from=created,until="9999-12-31";JSONArray history=task.optJSONArray("scheduleHistory");
      if(history!=null)for(int i=0;i<history.length();i++){JSONObject v=history.optJSONObject(i);if(v==null)continue;String day=v.optString("from");if(day.compareTo(today)<=0&&(selected==null||day.compareTo(from)>=0)){selected=v;from=day;}}
      if(history!=null&&selected==null)return false;
      if(history!=null)for(int i=0;i<history.length();i++){JSONObject v=history.optJSONObject(i);if(v!=null){String day=v.optString("from");if(day.compareTo(from)>0&&day.compareTo(until)<0)until=day;}}
      JSONObject marks=allMarks==null?null:allMarks.optJSONObject(task.optString("id"));
      JSONArray pauses=task.optJSONArray("archivePeriods");if(pauses!=null)for(int i=0;i<pauses.length();i++){JSONObject p=pauses.optJSONObject(i);if(p==null)continue;String end=p.optString("to","");if(p.optString("from").compareTo(today)<=0&&(end.isEmpty()||end.equals("null")||today.compareTo(end)<0))return false;}
      String mode=selected==null?task.optString("repeatMode",task.optBoolean("recurring",true)?"daily":"once"):selected.optString("mode","daily");
      if(mode.equals("once")){if(marks!=null){java.util.Iterator<String> keys=marks.keys();while(keys.hasNext()){String day=keys.next();if(day.compareTo(from)>=0&&day.compareTo(until)<0&&day.compareTo(today)<=0&&marks.optBoolean(day,false))return false;}}return true;}
      if(marks!=null&&marks.optBoolean(today,false))return false;
      if(mode.equals("weekdays")){JSONArray days=selected==null?task.optJSONArray("weekdays"):selected.optJSONArray("weekdays");if(days==null)return false;int weekday=LocalDate.parse(today).getDayOfWeek().getValue()-1;for(int i=0;i<days.length();i++)if(days.optInt(i,-1)==weekday)return true;return false;}
      int interval=mode.equals("weekly")?7:mode.equals("custom")?Math.max(2,Math.min(365,selected==null?task.optInt("repeatDays",2):selected.optInt("days",2))):1;
      String anchor=selected==null?created:selected.optString("anchor",from);return ChronoUnit.DAYS.between(LocalDate.parse(anchor),LocalDate.parse(today))%interval==0;
    }catch(Exception invalidTask){return false;}
  }
}
