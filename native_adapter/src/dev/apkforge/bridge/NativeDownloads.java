package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONObject;
import java.util.*;
import java.util.regex.*;

/** Replacement HLS downloads use the retained Exo cache/queue, without a DRM license. */
public final class NativeDownloads {
    private static final Map<String,String> VERIFIED=new LinkedHashMap<String,String>(16,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>64;}};
    static boolean asset(String id){return id!=null&&id.matches("ANI[0-9]+E[0-9]+D?");}
    static JSONObject benefits()throws Exception {
        org.json.JSONArray items=new org.json.JSONArray();for(String name:new String[]{"cr_premium","cr_fan_pack","offline_viewing","concurrent_streams.4"})items.put(new JSONObject().put("benefit",name).put("source","apkforge-local"));return new JSONObject().put("items",items);
    }
    static JSONObject prepare(String id,int resolution)throws Exception {
        if(!asset(id))throw new BackendBridge.HttpFailure(404,"unknown-download-asset");
        JSONObject streams=NativePlayback.resolve(id);String url=streams.getJSONObject("streams").getJSONObject("adaptive_hls").getJSONObject("").getString("url");
        String manifest=NativePlayback.get(url,NativePlayback.REFERER);String selected=variant(url,manifest,resolution);
        if(!selected.equals(url))manifest=NativePlayback.get(selected,NativePlayback.REFERER);
        validate(manifest);
        streams.getJSONObject("streams").getJSONObject("adaptive_hls").getJSONObject("").put("url",selected);
        // Keep real subtitles locally as ASS, so a completed download does not
        // need a subtitle URL/token which expires when the app is restarted.
        android.content.Context context=CloudSession.context(NativeDownloads.class.getClassLoader());String owner=CloudSession.userId();if(owner.isEmpty())throw new BackendBridge.HttpFailure(401,"login-required");
        java.io.File folder=new java.io.File(context.getFilesDir(),"apkforge-offline/"+owner);if(!folder.isDirectory()&&!folder.mkdirs())throw new java.io.IOException("Offline storage unavailable");
        JSONObject subtitles=streams.getJSONObject("subtitles");Iterator<String> languages=subtitles.keys();
        while(languages.hasNext()){String language=languages.next();JSONObject track=subtitles.getJSONObject(language);
            if(!language.matches("[a-z]{2,3}(?:-[A-Za-z0-9]{2,4})?"))continue;
            java.io.File file=new java.io.File(folder,id+"-"+language+".ass");
            try{String ass=NativeSubtitles.content(track.getString("url"));try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(ass.getBytes(java.nio.charset.StandardCharsets.UTF_8));}track.put("localFilePath",file.getAbsolutePath());}
            catch(Exception unavailable){throw new java.io.IOException("Subtitle download unavailable",unavailable);}
        }
        context.getSharedPreferences("apkforge_offline_"+owner,0).edit().putString(id,streams.toString()).apply();
        synchronized(VERIFIED){VERIFIED.put(id,selected);}
        return response(selected,subtitles);
    }
    static JSONObject response(String url,JSONObject subtitles)throws Exception {
        return new JSONObject().put("url",url).put("token","").put("subtitles",subtitles).put("captions",new JSONObject());
    }
    static String variant(String base,String manifest,int requested)throws Exception {
        if(!manifest.trim().startsWith("#EXTM3U"))throw new java.io.IOException("Invalid HLS manifest");
        String[] lines=manifest.split("\\r?\\n");String best="",smallest="";int bestHeight=0,smallestHeight=Integer.MAX_VALUE;int limit=Math.max(240,Math.min(2160,requested));
        for(int i=0;i<lines.length-1;i++)if(lines[i].startsWith("#EXT-X-STREAM-INF:")){
            Matcher size=Pattern.compile("RESOLUTION=[0-9]+x([0-9]+)").matcher(lines[i]);int h=size.find()?Integer.parseInt(size.group(1)):720;
            int next=i+1;while(next<lines.length&&(lines[next].trim().isEmpty()||lines[next].startsWith("#")))next++;if(next>=lines.length)continue;
            String url=new java.net.URI(base).resolve(lines[next].trim()).toString();NativePlayback.publicHttps(url);
            if(h<smallestHeight){smallestHeight=h;smallest=url;}if(h<=limit&&h>bestHeight){bestHeight=h;best=url;}
        }return !best.isEmpty()?best:!smallest.isEmpty()?smallest:base;
    }
    static void validate(String manifest)throws Exception {
        if(!manifest.trim().startsWith("#EXTM3U")||!manifest.contains("#EXT-X-ENDLIST"))throw new java.io.IOException("Only complete episode playlists can be downloaded");
        for(String line:manifest.split("\\r?\\n"))if(line.startsWith("#EXT-X-KEY:")){
            Matcher method=Pattern.compile("(?:^#EXT-X-KEY:|,)\\s*METHOD=([^,]+)").matcher(line);
            String value=method.find()?method.group(1).trim():"";
            Matcher format=Pattern.compile("(?:^#EXT-X-KEY:|,)\\s*KEYFORMAT=([^,]+)").matcher(line);
            boolean identity=!format.find()||"\"identity\"".equals(format.group(1).trim());
            if(!("NONE".equals(value)||"AES-128".equals(value))||!identity)throw new java.io.IOException("DRM download unsupported");
        }
    }
    /** Called only for our validated replacement assets; other content retains original behavior. */
    public static boolean start(Object manager,String id,Object stream){
        if(!asset(id))return false;
        try{String url=(String)stream.getClass().getMethod("getUrl").invoke(stream),expected;synchronized(VERIFIED){expected=VERIFIED.get(id);}if(expected==null||!expected.equals(url))throw new java.io.IOException("Unverified offline source");
            ClassLoader loader=manager.getClass().getClassLoader();Class<?> callback=Class.forName("com.ellation.crunchyroll.downloading.exoplayer.ExoPlayerLocalVideosManagerImpl$h",true,loader);
            Object prepare=callback.getConstructor(String.class,stream.getClass(),manager.getClass()).newInstance(id,stream,manager);
            callback.getMethod("invoke",Object.class).invoke(prepare,new Object[]{null});return true;
        }catch(Exception failure){
            android.util.Log.w("APKForgeDownloads","Download preparation failed: "+failure.getClass().getSimpleName());
            try{Class<?> callback=Class.forName("com.ellation.crunchyroll.downloading.exoplayer.ExoPlayerLocalVideosManagerImpl$i");Object error=callback.getConstructor(manager.getClass(),String.class).newInstance(manager,id);callback.getMethod("invoke",Object.class).invoke(error,failure);}catch(Exception report){android.util.Log.w("APKForgeDownloads","Download failure callback unavailable");}return true;
        }
    }
    static JSONObject completed(String id){
        if(!asset(id)||CloudSession.userId().isEmpty())return null;
        try{Class<?> module=Class.forName("uy.a");Object instance=module.getField("a").get(null),manager=module.getMethod("c").invoke(instance),index=manager.getClass().getField("b").get(manager);
            Object download=Class.forName("b6.u").getMethod("c",String.class).invoke(index,id);if(download==null||download.getClass().getField("b").getInt(download)!=3)return null;
            String raw=CloudSession.context(NativeDownloads.class.getClassLoader()).getSharedPreferences("apkforge_offline_"+CloudSession.userId(),0).getString(id,null);return raw==null?null:new JSONObject(raw);
        }catch(Exception unavailable){return null;}
    }
}
