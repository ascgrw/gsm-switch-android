package com.shahid.gsmcontrol;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;

final class Secrets {
    private static final String ALIAS="gsm-switch-private-pins";
    private static synchronized SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return (SecretKey)ks.getKey(ALIAS,null);
        KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return g.generateKey();
    }
    static void put(Context c,String name,String value) throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String encoded=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+
            Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!c.getSharedPreferences("private-pins",0).edit().putString(name,encoded).commit())throw new IllegalStateException("Could not save PIN");
    }
    static String get(Context c,String name) throws Exception {
        String s=c.getSharedPreferences("private-pins",0).getString(name,"");if(s.isEmpty())return "";
        String[] a=s.split(":",2);if(a.length!=2)throw new IllegalStateException("Invalid saved PIN");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(a[0],Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(a[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
    static void remove(Context c,String name){c.getSharedPreferences("private-pins",0).edit().remove(name).apply();}
}
