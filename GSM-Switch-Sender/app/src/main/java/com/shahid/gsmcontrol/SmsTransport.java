package com.shahid.gsmcontrol;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionInfo;
import java.util.List;
import java.util.UUID;

final class SmsTransport {
    private SmsTransport() {}
    @SuppressWarnings("deprecation")
    private static SmsManager manager(Context c){
        int selected=State.prefs(c).getInt("sim-id",-1);
        if(selected>=0){
            if(c.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Allow phone-state permission to use the selected SIM");
            List<SubscriptionInfo> active=c.getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();boolean found=false;
            if(active!=null)for(SubscriptionInfo s:active)if(s.getSubscriptionId()==selected)found=true;
            if(!found)throw new IllegalStateException("Selected SIM is no longer active; choose a SIM again");
        }else selected=Build.VERSION.SDK_INT>=24?SubscriptionManager.getDefaultSmsSubscriptionId():SmsManager.getDefault().getSubscriptionId();
        boolean valid=Build.VERSION.SDK_INT>=29?SubscriptionManager.isValidSubscriptionId(selected):selected>=0;
        if(!valid)throw new IllegalStateException("Choose a default SMS SIM in Android Settings, or choose a SIM in this app");
        return Build.VERSION.SDK_INT>=31?c.getSystemService(SmsManager.class).createForSubscriptionId(selected):SmsManager.getSmsManagerForSubscriptionId(selected);
    }
    static void send(Context c,String phone,String body,String rid,boolean receiverReply){
        if(c.checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("SMS send permission is not granted");
        if(!Protocol.phone(phone)||body==null||body.length()>150)throw new IllegalArgumentException("Invalid SMS recipient or command length");
        Intent result=new Intent(c,SentSmsReceiver.class).setAction("com.shahid.gsmcontrol.SMS_RESULT")
            .setData(Uri.parse("gsm-result://"+c.getPackageName()+"/"+UUID.randomUUID()))
            .putExtra("phone",phone).putExtra("rid",rid).putExtra("receiverReply",receiverReply);
        PendingIntent sent=PendingIntent.getBroadcast(c,0,result,PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
        manager(c).sendTextMessage(phone,null,body,sent,null);
    }
}
