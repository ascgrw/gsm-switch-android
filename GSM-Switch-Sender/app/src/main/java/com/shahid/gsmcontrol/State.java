package com.shahid.gsmcontrol;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import org.json.JSONArray;
import org.json.JSONObject;

final class State {
    private State() {}
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("gsm-native",0);}
    private static JSONArray records(Context c){try{return new JSONArray(prefs(c).getString("records","[]"));}catch(Exception e){return new JSONArray();}}
    private static void persistRecords(Context c,JSONArray a){if(!prefs(c).edit().putString("records",a.toString()).commit())throw new IllegalStateException("Could not save command history");}
    static synchronized boolean hasRequest(Context c,String phone,String id){JSONArray a=records(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&phone.equals(r.optString("phone"))&&id.equals(r.optString("rid")))return true;}return false;}
    static synchronized void addRequest(Context c,String phone,Protocol.Command cmd) throws Exception {
        if(hasRequest(c,phone,cmd.id))throw new IllegalArgumentException("Request ID already used; no SMS was sent");
        JSONArray old=records(c),a=new JSONArray();JSONObject r=new JSONObject();
        r.put("phone",phone).put("rid",cmd.id).put("action",cmd.action).put("created",System.currentTimeMillis()).put("status","Queued · awaiting SMS result").put("allowReply",false);a.put(r);
        for(int i=0;i<Math.min(old.length(),199);i++)a.put(old.get(i));persistRecords(c,a);
    }
    static synchronized void sentResult(Context c,String phone,String id,boolean sent,String detail){
        JSONArray a=records(c);for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null&&phone.equals(r.optString("phone"))&&id.equals(r.optString("rid"))){
            if(!r.optString("status").startsWith("Controller"))try{r.put("status",sent?"SMS sent · check reply in Messages":"SMS failed · "+detail);r.put("allowReply",false);}catch(Exception ignored){}
            break;
        }}persistRecords(c,a);
    }
    static synchronized void submitFailed(Context c,String phone,String id,String detail){sentResult(c,phone,id,false,detail);}
    static synchronized JSONObject snapshot(Context c){JSONObject out=new JSONObject();try{
        out.put("sendPermission",c.checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED)
            .put("senderOnly",true).put("simLabel",prefs(c).getString("sim-label","Default SIM")).put("records",records(c));
    }catch(Exception ignored){}return out;}
}
