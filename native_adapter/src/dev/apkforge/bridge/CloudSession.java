package dev.apkforge.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.net.ssl.HttpsURLConnection;

/** Optional cloud session, independent of original-service credentials. */
final class CloudSession {
    static final String URL_BASE="https://yhccrdatocqqniblpshm.supabase.co";
    // Public client identifier only. Database access is governed by user JWT + RLS.
    private static final String PUBLIC_KEY="sb_publishable_AznFcuP_AhGEzZa4NNROQA_64FV8DUZ";
    private static final String KEY_ALIAS="apkforge_supabase_session_v1";
    private static SharedPreferences prefs;
    private static JSONObject session;
    static Context context(ClassLoader loader)throws Exception {
        return (Context)Class.forName("com.ellation.crunchyroll.application.e",true,loader).getMethod("b").invoke(null);
    }
    static synchronized void init(ClassLoader loader)throws Exception {
        if(prefs!=null)return;prefs=context(loader).getSharedPreferences("apkforge_cloud_session",Context.MODE_PRIVATE);
        String saved=prefs.getString("session",null);if(saved==null)return;
        try{
            byte[] packed=Base64.decode(saved,Base64.NO_WRAP);if(packed.length<29)throw new IOException("Invalid session");
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,java.util.Arrays.copyOfRange(packed,0,12)));
            session=new JSONObject(new String(cipher.doFinal(packed,12,packed.length-12),StandardCharsets.UTF_8));
            validate(session);
        }catch(Exception e){prefs.edit().remove("session").commit();session=null;}
    }
    private static javax.crypto.SecretKey key()throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(KEY_ALIAS)){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());generator.generateKey();
        }
        return (javax.crypto.SecretKey)store.getKey(KEY_ALIAS,null);
    }
    private static void save(JSONObject value)throws Exception {
        // Never persist the submitted password; only the returned session is encrypted.
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream packed=new ByteArrayOutputStream();packed.write(cipher.getIV());packed.write(encrypted);
        if(!prefs.edit().putString("session",Base64.encodeToString(packed.toByteArray(),Base64.NO_WRAP)).commit())throw new IOException("Could not save session");session=value;
    }
    static synchronized String userId(){return session==null?"":session.optJSONObject("user").optString("id");}
    static synchronized String email(){return session==null?"":session.optJSONObject("user").optString("email");}
    static synchronized void signIn(String email,String password)throws Exception {
        if(email==null||password==null||email.trim().isEmpty()||password.isEmpty())throw new BackendBridge.HttpFailure(400,"missing-login-fields");
        JSONObject value=new JSONObject(request("/auth/v1/token?grant_type=password","POST",new JSONObject().put("email",email.trim()).put("password",password),null));
        validate(value);save(value);
    }
    static synchronized void signUp(JSONObject body)throws Exception {
        String email=body.optString("email","").trim(),password=body.optString("password","");
        if(!email.contains("@")||password.length()<8)throw new BackendBridge.HttpFailure(400,"invalid-signup-fields");
        JSONObject response=new JSONObject(request("/auth/v1/signup","POST",new JSONObject().put("email",email).put("password",password),null));
        // Confirmation-enabled projects return a user without a session.
        if(!response.optString("access_token","").isEmpty()){validate(response);save(response);}
    }
    static void recover(String email)throws Exception {
        if(email==null||!email.contains("@"))throw new BackendBridge.HttpFailure(400,"invalid-email");
        request("/auth/v1/recover","POST",new JSONObject().put("email",email.trim()),null);
    }
    static String rest(String path,String method,JSONObject body,boolean authenticated)throws Exception {
        if(!path.startsWith("/rest/v1/"))throw new IllegalArgumentException("Invalid REST path");
        return request(path,method,body,authenticated?token():null);
    }
    private static void validate(JSONObject value)throws Exception {
        JSONObject user=value.optJSONObject("user");
        if(value.optString("access_token").isEmpty()||value.optString("refresh_token").isEmpty()||user==null||!user.optString("id").matches("[0-9a-fA-F-]{36}"))throw new IOException("Invalid cloud session response");
        if(!value.has("expires_at"))value.put("expires_at",System.currentTimeMillis()/1000+value.optLong("expires_in",3600));
    }
    private static synchronized String token()throws Exception {
        if(session==null)throw new BackendBridge.HttpFailure(401,"cloud-login-required");
        if(session.optLong("expires_at")*1000<System.currentTimeMillis()+60000){
            JSONObject next=new JSONObject(request("/auth/v1/token?grant_type=refresh_token","POST",new JSONObject().put("refresh_token",session.getString("refresh_token")),null));
            validate(next);if(!next.getJSONObject("user").getString("id").equals(userId()))throw new IOException("Cloud identity changed");save(next);
        }
        return session.getString("access_token");
    }
    static synchronized void signOut()throws Exception {
        if(session!=null){try{request("/auth/v1/logout","POST",new JSONObject(),token());}finally{prefs.edit().remove("session").commit();session=null;}}
    }
    static JSONObject readSnapshot(String owner)throws Exception {
        if(!owner.equals(userId()))throw new BackendBridge.HttpFailure(401,"cloud-account-changed");
        JSONArray rows=new JSONArray(request("/rest/v1/account_state?user_id=eq."+owner+"&select=snapshot","GET",null,token()));
        return rows.length()==0?null:rows.getJSONObject(0).getJSONObject("snapshot");
    }
    static void writeSnapshot(String owner,JSONObject snapshot)throws Exception {
        if(!owner.equals(userId()))throw new BackendBridge.HttpFailure(401,"cloud-account-changed");
        request("/rest/v1/account_state?on_conflict=user_id","POST",new JSONObject().put("user_id",owner).put("snapshot",snapshot)
            .put("updated_at",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US){{setTimeZone(java.util.TimeZone.getTimeZone("UTC"));}}.format(new java.util.Date())),token());
    }
    private static String request(String path,String method,JSONObject body,String token)throws Exception {
        HttpsURLConnection connection=(HttpsURLConnection)new URL(URL_BASE+path).openConnection();
        connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setRequestMethod(method);
        connection.setRequestProperty("apikey",PUBLIC_KEY);connection.setRequestProperty("Accept","application/json");
        if(token!=null)connection.setRequestProperty("Authorization","Bearer "+token);
        if(path.startsWith("/rest/"))connection.setRequestProperty("Prefer",path.contains("/account_state")?"resolution=merge-duplicates,return=minimal":path.contains("/app_comment_votes")?"resolution=ignore-duplicates,return=representation":"return=representation");
        try{
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
            int status=connection.getResponseCode();
            if(status<200||status>=300)throw new BackendBridge.HttpFailure(status,"cloud-request-failed");
            try(InputStream in=connection.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
                while((count=in.read(buffer))!=-1){if(bytes.size()+count>2*1024*1024)throw new IOException("Cloud response too large");bytes.write(buffer,0,count);}return new String(bytes.toByteArray(),StandardCharsets.UTF_8);}
        }finally{connection.disconnect();}
    }
}
