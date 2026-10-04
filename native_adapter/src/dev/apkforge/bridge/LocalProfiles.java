package dev.apkforge.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Replacement-backend guest profiles; never reads the original credential store. */
final class LocalProfiles {
    private final SharedPreferences prefs;
    LocalProfiles(ClassLoader loader) throws Exception {
        Context app=(Context)Class.forName("com.ellation.crunchyroll.application.e",true,loader).getMethod("b").invoke(null);
        prefs=app.getSharedPreferences("apkforge_guest_profiles",Context.MODE_PRIVATE);
    }
    synchronized String accountId() {
        String id=prefs.getString("account",null);
        if(id==null){id=UUID.randomUUID().toString();prefs.edit().putString("account",id).commit();}
        return id;
    }
    private JSONArray load() throws Exception {
        String saved=prefs.getString("profiles",null);
        if(saved!=null)return new JSONArray(saved);
        String id=UUID.randomUUID().toString();
        JSONArray profiles=new JSONArray().put(new JSONObject().put("profile_id",id).put("profile_name","Guest").put("username","Guest")
            .put("avatar","default.png").put("wallpaper","default.png").put("maturity_rating","MATURE_CONTENT_DISABLED")
            .put("preferred_content_audio_language","ja-JP").put("preferred_content_subtitle_language","en-US")
            .put("preferred_communication_language","en-US").put("is_primary",true).put("can_switch",true));
        prefs.edit().putString("profiles",profiles.toString()).putString("selected",id).commit();return profiles;
    }
    synchronized String selectedId() throws Exception { load();return prefs.getString("selected",""); }
    synchronized JSONObject state(String category) throws Exception {return new JSONObject(prefs.getString(category+":"+selectedId(),"{}"));}
    synchronized void saveState(String category,JSONObject state) throws Exception {
        if(!prefs.edit().putString(category+":"+selectedId(),state.toString()).commit())throw new java.io.IOException("Could not persist profile state");
    }
    synchronized void select(String id) throws Exception {
        JSONArray profiles=load();
        for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).getString("profile_id").equals(id)){
            prefs.edit().putString("selected",id).commit();return;
        }
        throw new BackendBridge.HttpFailure(404,"profile-not-found");
    }
    synchronized JSONObject account() throws Exception {
        return new JSONObject().put("account_id",accountId()).put("external_id","0").put("created","2026-10-04T00:00:00Z")
            .put("email","").put("phone","").put("has_password",false);
    }
    synchronized JSONObject route(String path,String method,JSONObject body) throws Exception {
        JSONArray profiles=load();String selected=selectedId();
        if(path.endsWith("/usernames"))return new JSONObject().put("usernames",new JSONArray());
        if(path.endsWith("/multiprofile")){
            if(method.equals("GET")){
                for(int i=0;i<profiles.length();i++){JSONObject p=profiles.getJSONObject(i);p.put("is_selected",p.getString("profile_id").equals(selected));}
                return new JSONObject().put("max_profiles",5).put("profiles",profiles);
            }
            if(method.equals("POST")){
                if(profiles.length()>=5)throw new BackendBridge.HttpFailure(409,"profile-limit");
                JSONObject p=new JSONObject(profiles.getJSONObject(0).toString()).put("profile_id",UUID.randomUUID().toString()).put("is_primary",false);
                apply(p,body);profiles.put(p);save(profiles);
                return new JSONObject().put("profile_id",p.getString("profile_id")).put("account_id",accountId());
            }
        }
        String id=path.substring(path.lastIndexOf('/')+1);
        for(int i=0;i<profiles.length();i++){
            JSONObject p=profiles.getJSONObject(i);if(!p.getString("profile_id").equals(id))continue;
            if(method.equals("GET"))return p.put("is_selected",id.equals(selected));
            if(method.equals("PATCH")){apply(p,body);save(profiles);return new JSONObject();}
            if(method.equals("DELETE")){
                if(p.optBoolean("is_primary")||profiles.length()==1)throw new BackendBridge.HttpFailure(409,"primary-profile-required");
                profiles.remove(i);save(profiles);
                if(id.equals(selected))prefs.edit().putString("selected",profiles.getJSONObject(0).getString("profile_id")).commit();
                return new JSONObject();
            }
        }
        throw new BackendBridge.HttpFailure(404,"profile-not-found");
    }
    private void apply(JSONObject p,JSONObject body) throws Exception {
        for(String key:new String[]{"profile_name","username","avatar","wallpaper","maturity_rating","preferred_content_audio_language","preferred_content_subtitle_language"}){
            if(body.has(key)&&!body.isNull(key))p.put(key,body.getString(key));
        }
        String name=p.optString("profile_name").trim();
        if(name.isEmpty()||name.length()>32)throw new BackendBridge.HttpFailure(400,"invalid-profile-name");
        p.put("profile_name",name);
    }
    private void save(JSONArray profiles) throws Exception {
        if(!prefs.edit().putString("profiles",profiles.toString()).commit())throw new java.io.IOException("Could not persist profiles");
    }
}
