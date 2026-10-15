package com.shahid.gsmcontrol;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.SmsManager;

public final class SentSmsReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i){
        if(!"com.shahid.gsmcontrol.SMS_RESULT".equals(i.getAction()))return;
        int code=getResultCode();boolean sent=code==Activity.RESULT_OK;
        String detail=code==SmsManager.RESULT_ERROR_NO_SERVICE?"no mobile service":code==SmsManager.RESULT_ERROR_RADIO_OFF?"radio is off":code==SmsManager.RESULT_ERROR_LIMIT_EXCEEDED?"SMS rate limit":"Android result "+code;
        if(i.getBooleanExtra("receiverReply",false))State.prefs(c).edit().putString("test-reply-status",sent?"Test reply SMS sent":"Test reply failed · "+detail).apply();
        else State.sentResult(c,i.getStringExtra("phone"),i.getStringExtra("rid"),sent,detail);
    }
}
