package com.shahid.gsmcontrol;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.text.InputType;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String HOST="appassets.gsm.local";
    private WebView web;
    private String exportData="";
    private ValueCallback<Uri[]> fileChooser;
    private final Bridge bridge=new Bridge();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0c1312);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        web=new WebView(this);web.setBackgroundColor(0xff0c1312);root.addView(web,new LinearLayout.LayoutParams(-1,-1));setContentView(root);
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(true);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(bridge,"GsmAndroid");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView w,WebResourceRequest r){return !trusted(r.getUrl());}
            @Override public WebResourceResponse shouldInterceptRequest(WebView w,WebResourceRequest request){
                Uri u=request.getUrl();String path=u.getPath();
                if(!trusted(u)||path==null||!(path.equals("/index.html")||path.equals("/app.js")||path.equals("/downloads/Circuit.svg")))return blocked();
                try{
                    String mime=path.endsWith(".js")?"application/javascript":path.endsWith(".svg")?"image/svg+xml":"text/html";
                    Map<String,String> headers=new HashMap<>();headers.put("Cache-Control","no-store");
                    headers.put("Content-Security-Policy","default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-src 'none'; connect-src 'none'");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open(path.substring(1)));
                }catch(Exception e){return blocked();}
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView w,ValueCallback<Uri[]> callback,FileChooserParams params){
                if(fileChooser!=null)fileChooser.onReceiveValue(null);fileChooser=callback;
                Intent choose=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
                try{startActivityForResult(choose,201);}catch(Exception e){fileChooser.onReceiveValue(null);fileChooser=null;message("No file picker available");}
                return true;
            }
        });
        web.loadUrl("https://"+HOST+"/index.html");
    }
    private boolean trusted(Uri u){return "https".equals(u.getScheme())&&HOST.equals(u.getHost())&&(u.getPort()==-1||u.getPort()==443);}
    private WebResourceResponse blocked(){return new WebResourceResponse("text/plain","UTF-8",403,"Blocked",new HashMap<>(),new ByteArrayInputStream("Only bundled controller pages are permitted".getBytes(StandardCharsets.UTF_8)));}
    private void message(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    private EditText field(LinearLayout parent,String label,String value,boolean password){
        TextView title=new TextView(this);title.setText(label);title.setTextSize(15);title.setPadding(0,20,0,4);parent.addView(title);
        EditText e=new EditText(this);e.setText(value);e.setSingleLine();e.setInputType(password?InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD:InputType.TYPE_CLASS_PHONE);parent.addView(e);return e;
    }
    private void requestSms(){
        ArrayList<String> required=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.SEND_SMS})if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)required.add(p);
        if(required.isEmpty()){message("SMS permissions are already allowed");return;}
        requestPermissions(required.toArray(new String[0]),100);
    }
    private void chooseSim(){
        if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE},101);return;}
        try{
            List<SubscriptionInfo> list=getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
            if(list==null||list.isEmpty()){message("No active SIM found");return;}
            String[] labels=new String[list.size()+1];labels[0]="Default SIM";
            for(int i=0;i<list.size();i++){SubscriptionInfo s=list.get(i);labels[i+1]="SIM "+(s.getSimSlotIndex()+1)+" · "+s.getDisplayName();}
            new AlertDialog.Builder(this).setTitle("Sending SIM")
                .setItems(labels,(d,n)->{State.prefs(this).edit().putInt("sim-id",n==0?-1:list.get(n-1).getSubscriptionId()).putString("sim-label",labels[n]).apply();message("SIM saved");}).setNegativeButton("Cancel",null).show();
        }catch(Exception e){message("Unable to list SIMs: "+e.getMessage());}
    }
    private void showSettings(){
        LinearLayout form=new LinearLayout(this);form.setOrientation(LinearLayout.VERTICAL);form.setPadding(28,12,28,12);
        TextView intro=new TextView(this);intro.setText("Replies appear in Messages. SMS charges apply.");form.addView(intro);
        TextView status=new TextView(this);status.setPadding(0,15,0,0);status.setText("Send SMS permission: "+(checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED?"allowed":"not allowed"));form.addView(status);
        android.widget.Button permission=new android.widget.Button(this);permission.setText("Allow Send SMS");permission.setOnClickListener(v->requestSms());form.addView(permission);
        android.widget.Button sim=new android.widget.Button(this);sim.setText("Choose SIM");sim.setOnClickListener(v->chooseSim());form.addView(sim);
        ScrollView scroll=new ScrollView(this);scroll.addView(form);
        new AlertDialog.Builder(this).setTitle("SMS setup").setView(scroll).setPositiveButton("Close",null).show();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==101){if(checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED)chooseSim();else message("Choose a default SMS SIM in phone Settings");}else message(checkSelfPermission(Manifest.permission.SEND_SMS)==PackageManager.PERMISSION_GRANTED?"SMS enabled":"SMS permission is needed to send commands");}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==201){if(fileChooser!=null){fileChooser.onReceiveValue(result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null);fileChooser=null;}return;}if(request!=200)return;
        String body=exportData;exportData="";if(result==RESULT_OK&&data!=null&&data.getData()!=null)try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){if(stream==null)throw new IllegalStateException("Unable to open file");stream.write(body.getBytes(StandardCharsets.UTF_8));message("Backup saved");}catch(Exception e){message("Could not save backup");}
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){if(fileChooser!=null){fileChooser.onReceiveValue(null);fileChooser=null;}web.removeJavascriptInterface("GsmAndroid");web.destroy();super.onDestroy();}
    public final class Bridge {
        @JavascriptInterface public String snapshot(){return State.snapshot(MainActivity.this).toString();}
        @JavascriptInterface public void openSettings(){runOnUiThread(()->showSettings());}
        @JavascriptInterface public void requestSms(){runOnUiThread(()->MainActivity.this.requestSms());}
        @JavascriptInterface public void chooseSim(){runOnUiThread(()->MainActivity.this.chooseSim());}
        @JavascriptInterface public boolean hasPin(String phone){if(!Protocol.phone(phone))return false;try{return Protocol.pin(Secrets.get(MainActivity.this,"controller:"+phone));}catch(Exception e){return false;}}
        @JavascriptInterface public String savePin(String phone,String pin){try{if(!Protocol.phone(phone)||!Protocol.pin(pin))throw new IllegalArgumentException("Invalid phone number or PIN");Secrets.put(MainActivity.this,"controller:"+phone,pin);return new JSONObject().put("ok",true).toString();}catch(Exception e){return error(e.getMessage());}}
        @JavascriptInterface public void forgetPin(String phone){if(Protocol.phone(phone))Secrets.remove(MainActivity.this,"controller:"+phone);}
        @JavascriptInterface public String sendCommand(String phone,String rid,String action){
            boolean recorded=false;
            try{
                Protocol.Command cmd=Protocol.parse(rid,action);if(!Protocol.phone(phone)||cmd==null)throw new IllegalArgumentException("Invalid recipient or command");
                if(checkSelfPermission(Manifest.permission.SEND_SMS)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Enable SMS in Settings");
                String pin=Secrets.get(MainActivity.this,"controller:"+phone);if(!Protocol.pin(pin))throw new IllegalStateException("Save this controller's PIN first");
                State.addRequest(MainActivity.this,phone,cmd);recorded=true;
                SmsTransport.send(MainActivity.this,phone,pin+" "+rid+" "+action,rid,false);
                return new JSONObject().put("ok",true).put("status","Queued · awaiting SMS result").toString();
            }catch(Exception e){if(recorded)State.submitFailed(MainActivity.this,phone,rid,e.getMessage());return error(e.getMessage());}
        }
        @JavascriptInterface public void exportFile(String name,String data){
            if(data==null||data.length()>1000000||!(name.equals("GSM-Switch-Backup.json")||name.equals("GSM-Switch-History.json")))return;
            runOnUiThread(()->{if(!exportData.isEmpty()){message("Finish the current save first");return;}exportData=data;Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,name);try{startActivityForResult(intent,200);}catch(Exception e){exportData="";message("No file picker available");}});
        }
        private String error(String text){try{return new JSONObject().put("ok",false).put("error",text==null?"SMS operation failed":text).toString();}catch(Exception e){return "{\"ok\":false,\"error\":\"SMS operation failed\"}";}}
    }
}
