package dev.apkforge.bridge;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;

/** Separate replacement account profiles; never reads original-service credentials. */
final class LocalProfiles {
    private final SharedPreferences prefs;
    final String cloudOwner;
    private boolean cloudReady;
    private static final java.util.concurrent.ExecutorService SYNC=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicReference<JSONObject> pending=new java.util.concurrent.atomic.AtomicReference<>();
    private final java.util.concurrent.atomic.AtomicBoolean syncing=new java.util.concurrent.atomic.AtomicBoolean();
    LocalProfiles(ClassLoader loader) throws Exception {
        Context app=CloudSession.context(loader);cloudOwner=CloudSession.userId();
        prefs=app.getSharedPreferences(cloudOwner.isEmpty()?"apkforge_guest_profiles":"apkforge_profiles_"+cloudOwner,Context.MODE_PRIVATE);
        cloudReady=prefs.getBoolean("cloud_restored",false);
        if(!cloudOwner.isEmpty()&&!cloudReady){
            try{JSONObject saved=CloudSession.readSnapshot(cloudOwner);if(saved!=null){
                // A failed first restore may have left local edits. Preserve both
                // copies rather than silently overwrite either one on reconnect.
                if(prefs.contains("profiles"))throw new java.io.IOException("Restore requires reconciliation");
                JSONArray restored=new JSONArray(saved.getString("profiles"));
                if(restored.length()<1||restored.length()>5)throw new java.io.IOException("Invalid profile snapshot");
                java.util.HashSet<String> ids=new java.util.HashSet<>();
                for(int i=0;i<restored.length();i++){
                    JSONObject record=restored.getJSONObject(i);String id=record.getString("profile_id"),name=record.getString("profile_name").trim();
                    if(!id.matches("[0-9a-fA-F-]{36}")||!ids.add(id)||name.isEmpty()||name.length()>32)throw new java.io.IOException("Invalid profile snapshot");
                }
                if(!ids.contains(saved.getString("selected")))throw new java.io.IOException("Invalid selected profile");
                SharedPreferences.Editor edit=prefs.edit();java.util.Iterator<String> keys=saved.keys();
                while(keys.hasNext()){String key=keys.next();if(allowed(key))edit.putString(key,saved.getString(key));}
                if(!edit.commit())throw new java.io.IOException("Could not restore profiles");
            }cloudReady=true;prefs.edit().putBoolean("cloud_restored",true).commit();}
            catch(Exception e){android.util.Log.w("APKForgeCloud","Cloud profile restore unavailable; using device state without overwriting cloud");}
        }
    }
    synchronized String accountId() {
        if(!cloudOwner.isEmpty())return cloudOwner;
        String id=prefs.getString("account",null);
        if(id==null){id=UUID.randomUUID().toString();prefs.edit().putString("account",id).commit();}
        return id;
    }
    private JSONArray load() throws Exception {
        String saved=prefs.getString("profiles",null);
        if(saved!=null){
            JSONArray records=new JSONArray(saved);boolean changed=false;
            if(!cloudOwner.isEmpty())for(int i=0;i<records.length();i++){JSONObject p=records.getJSONObject(i);
                if(p.optBoolean("is_primary")&&"Guest".equals(p.optString("profile_name"))&&"Guest".equals(p.optString("username"))){p.put("profile_name","Profile 1").put("username","Profile 1");changed=true;}}
            if(changed)prefs.edit().putString("profiles",records.toString()).commit();return records;
        }
        String id=UUID.randomUUID().toString();
        String name=cloudOwner.isEmpty()?"Guest":"Profile 1";
        JSONArray profiles=new JSONArray().put(new JSONObject().put("profile_id",id).put("profile_name",name).put("username",name)
            .put("avatar","default.png").put("wallpaper","default.png").put("maturity_rating","MATURE_CONTENT_DISABLED")
            .put("preferred_content_audio_language","ja-JP").put("preferred_content_subtitle_language","en-US")
            .put("preferred_communication_language","en-US").put("is_primary",true).put("can_switch",true));
        prefs.edit().putString("profiles",profiles.toString()).putString("selected",id).commit();return profiles;
    }
    synchronized String selectedId() throws Exception { load();return prefs.getString("selected",""); }
    synchronized JSONObject state(String category) throws Exception {return new JSONObject(prefs.getString(category+":"+selectedId(),"{}"));}
    synchronized void saveState(String category,JSONObject state) throws Exception {
        if(!prefs.edit().putString(category+":"+selectedId(),state.toString()).commit())throw new java.io.IOException("Could not persist profile state");
        sync();
    }
    synchronized void select(String id) throws Exception {
        JSONArray profiles=load();
        for(int i=0;i<profiles.length();i++)if(profiles.getJSONObject(i).getString("profile_id").equals(id)){
            prefs.edit().putString("selected",id).commit();sync();return;
        }
        throw new BackendBridge.HttpFailure(404,"profile-not-found");
    }
    synchronized JSONObject account() throws Exception {
        return new JSONObject().put("account_id",accountId()).put("external_id","0").put("created","2026-10-04T00:00:00Z")
            .put("email",cloudOwner.isEmpty()?"":CloudSession.email()).put("phone","").put("has_password",!cloudOwner.isEmpty());
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
                profiles.remove(i);SharedPreferences.Editor edit=prefs.edit().putString("profiles",profiles.toString());
                if(id.equals(selected))edit.putString("selected",profiles.getJSONObject(0).getString("profile_id"));
                for(String category:new String[]{"watchlist","playheads","ratings","custom-lists"})edit.remove(category+":"+id);
                if(!edit.commit())throw new java.io.IOException("Could not delete profile state");sync();
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
        sync();
    }
    private static boolean allowed(String key){return key.equals("profiles")||key.equals("selected")||key.matches("(?:watchlist|playheads|ratings|custom-lists):[0-9a-fA-F-]{36}");}
    private void sync()throws Exception {
        if(cloudOwner.isEmpty()||!cloudReady)return;JSONObject snapshot=new JSONObject();
        for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet())if(allowed(entry.getKey())&&entry.getValue() instanceof String)snapshot.put(entry.getKey(),entry.getValue());
        pending.set(snapshot);
        if(!syncing.compareAndSet(false,true))return;
        SYNC.execute(()->{
            try{JSONObject next;while((next=pending.getAndSet(null))!=null){try{CloudSession.writeSnapshot(cloudOwner,next);}catch(Exception e){android.util.Log.w("APKForgeCloud","Cloud sync unavailable; device state retained");}}}
            finally{syncing.set(false);if(pending.get()!=null){try{synchronized(this){sync();}}catch(Exception ignored){}}}
        });
    }
}
