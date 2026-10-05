package dev.apkforge.bridge;

import android.util.Base64;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** Native HLS adapter. Resolves public provider responses; never runs embed scripts. */
public final class NativePlayback {
    static final String UA="Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    static final String REFERER="https://megaplay.buzz/";
    private static final ThreadPoolExecutor WORKERS=new ThreadPoolExecutor(2,2,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(8));
    private static final Pattern ASSET=Pattern.compile("ANI([0-9]+)E([0-9]+)(D)?");
    private static final Pattern DATA_ID=Pattern.compile("data-id\\s*=\\s*[\"']([0-9]{1,12})[\"']");
    private static final LinkedHashMap<String,Cached> CACHE=new LinkedHashMap<String,Cached>(16,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,Cached> e){return size()>16;}
    };
    private static final Map<String,String> SKIPS=new LinkedHashMap<String,String>(32,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>64;}
    };
    private static final class Cached {final long time=android.os.SystemClock.elapsedRealtime();final String json;Cached(JSONObject j){json=j.toString();}}

    public static Object streams(Object asset)throws IOException {
        try {
            String id=(String)asset.getClass().getMethod("getId").invoke(asset);
            Log.i("APKForgeNative","Resolving native HLS "+id);
            JSONObject local=NativeDownloads.completed(id);
            return model("com.ellation.crunchyroll.api.cms.model.streams.Streams",local==null?resolve(id):local);
        }catch(IOException e){throw e;}catch(Exception e){throw new IOException("Native stream mapping failed",e);}
    }
    /** Retains Kotlin suspension and dispatch, keeping provider I/O off the UI thread. */
    public static Object streamsAsync(Object asset,Object continuation)throws IOException {
        try {
            Class<?> cont=Class.forName("zc0.d");
            Object dispatched=Class.forName("ad0.b").getMethod("D",cont).invoke(null,continuation);
            Object safe=Class.forName("zc0.i").getConstructor(cont).newInstance(dispatched);
            WORKERS.execute(()->{
                Object result;
                try{result=streams(asset);}catch(Throwable e){
                    try{result=Class.forName("vc0.m").getMethod("a",Throwable.class).invoke(null,e);}
                    catch(Exception unexpected){Log.e("APKForgeNative","Continuation error mapping failed");return;}
                }
                try{cont.getMethod("resumeWith",Object.class).invoke(safe,result);}
                catch(Exception e){Log.e("APKForgeNative","Continuation resume failed");}
            });
            return safe.getClass().getMethod("a").invoke(safe);
        }catch(Exception e){throw new IOException("Native resolver dispatch failed",e);}
    }
    static JSONObject resolve(String asset)throws Exception {
        Matcher m=ASSET.matcher(asset);if(!m.matches())throw new IOException("Unsupported replacement asset");
        String preferred=BackendBridge.preferredSubtitleLanguage(),cacheKey=asset+":"+preferred;
        synchronized(CACHE){Cached c=CACHE.get(cacheKey);if(c!=null&&android.os.SystemClock.elapsedRealtime()-c.time<60000)return new JSONObject(c.json);}
        String audio=m.group(3)==null?"sub":"dub";
        JSONObject response=new JSONObject(get("https://anivexaapi-aniko2.hf.space/api/watch/"+m.group(1)+"/"+audio+"/"+m.group(2),null));
        JSONObject bundle=response.optJSONObject(audio.equals("sub")?"ssub":"sdub");
        if(bundle==null)throw new IOException("Requested audio unavailable");
        JSONArray candidates=bundle.optJSONArray("streams");if(candidates==null)throw new IOException("No sources");
        HashSet<String> tried=new HashSet<>();
        // Prefer an explicitly labelled burned-in source only for the selected language.
        for(int pass=0;pass<2;pass++)for(int i=0;i<Math.min(candidates.length(),8);i++){
            JSONObject source=candidates.optJSONObject(i);if(source==null)continue;
            String sourceHard=hardLocale(source);
            if(pass==0&&!sourceHard.equals(preferred))continue;
            if(pass==1&&!sourceHard.isEmpty()&&!sourceHard.equals(preferred))continue;
            String embed=source.optString("url");
            if(!embedHost(embed)||!tried.add(embed))continue;
            try {
                Matcher id=DATA_ID.matcher(get(embed,REFERER));if(!id.find())continue;
                JSONObject sources=new JSONObject(get(REFERER+"stream/getSources?id="+id.group(1),embed));
                JSONObject decoded=decodeSources(sources.getString("enc"));
                String file=decoded.getString("file");publicHttps(file);
                // Reject HTML/error wrappers before passing a URL to the original player.
                String manifest=get(file,REFERER);if(!manifest.trim().startsWith("#EXTM3U"))continue;
                String hard=hardLocale(decoded);if(hard.isEmpty())hard=hardLocale(sources);if(hard.isEmpty())hard=sourceHard;
                if(!hard.isEmpty()&&!hard.equals(preferred))continue;
                JSONArray tracks=new JSONArray();appendTracks(tracks,bundle.optJSONArray("subtitles"));appendTracks(tracks,sources.optJSONArray("tracks"));appendTracks(tracks,decoded.optJSONArray("tracks"));
                JSONObject result=streamJson(asset,file,audio,tracks);
                result.getJSONObject("streams").getJSONObject("adaptive_hls").getJSONObject("").put("hardsub_locale",hard);
                if(!hard.isEmpty())result.put("subtitles",new JSONObject());
                JSONObject skips=mapSkipEvents(asset,sources);
                if(!skips.has("intro")&&!skips.has("credits"))skips=mapSkipEvents(asset,bundle);
                synchronized(SKIPS){SKIPS.put(asset,skips.toString());}
                synchronized(CACHE){CACHE.put(cacheKey,new Cached(result));}
                Log.i("APKForgeNative","HLS manifest verified for "+asset);
                return result;
            }catch(Exception e){Log.w("APKForgeNative","Source unavailable: "+e.getClass().getSimpleName());}
        }
        throw new IOException("No playable HLS source for requested episode/audio");
    }
    static JSONObject skipEvents(String asset)throws Exception {
        if(!ASSET.matcher(asset).matches())throw new BackendBridge.HttpFailure(404,"unknown-skip-asset");
        synchronized(SKIPS){String data=SKIPS.get(asset);if(data!=null)return new JSONObject(data);}
        // Metadata is requested before stream resolution in the retained player.
        // Obtain the same public bundle without resolving media a second time.
        Matcher match=ASSET.matcher(asset);match.matches();String audio=match.group(3)==null?"sub":"dub";
        try {
            JSONObject response=new JSONObject(get("https://anivexaapi-aniko2.hf.space/api/watch/"+match.group(1)+"/"+audio+"/"+match.group(2),null));
            JSONObject bundle=response.optJSONObject(audio.equals("sub")?"ssub":"sdub");
            JSONObject skips=mapSkipEvents(asset,bundle);
            if(skips.has("intro")||skips.has("credits")){synchronized(SKIPS){SKIPS.put(asset,skips.toString());}return skips;}
            // Source response can include skip ranges absent from the bundle.
            resolve(asset);synchronized(SKIPS){String data=SKIPS.get(asset);if(data!=null)return new JSONObject(data);}
        }catch(Exception unavailable){Log.w("APKForgeNative","Skip metadata unavailable; playback remains available");}
        return new JSONObject().put("mediaId",asset);
    }
    static JSONObject mapSkipEvents(String asset,JSONObject provider)throws Exception {
        JSONObject result=new JSONObject().put("mediaId",asset);if(provider==null)return result;
        String series=asset.substring(0,asset.indexOf('E'));
        for(String name:new String[]{"intro","outro"}){
            JSONObject range=provider.optJSONObject(name);if(range==null)continue;
            double start=range.optDouble("start",-1),end=range.optDouble("end",-1);
            if(Double.isNaN(start)||Double.isInfinite(start)||Double.isNaN(end)||Double.isInfinite(end)||start<0||end<=start||end>86400)continue;
            String field=name.equals("intro")?"intro":"credits";
            result.put(field,new JSONObject().put("start",start).put("end",end).put("seriesId",series).put("type",name.equals("intro")?"INTRO":"CREDITS"));
        }return result;
    }
    static JSONObject decodeSources(String enc)throws Exception {
        if(enc.length()>131072)throw new IOException("Source payload too large");
        byte[] key=Arrays.copyOf("i?LMTAx0Q6,:}50U".getBytes(StandardCharsets.UTF_8),32);
        Cipher cipher=Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new IvParameterSpec("W0;27ToaUpl_P%'c".getBytes(StandardCharsets.UTF_8)));
        return new JSONObject(new String(cipher.doFinal(Base64.decode(enc,Base64.URL_SAFE|Base64.NO_WRAP)),StandardCharsets.UTF_8));
    }
    static JSONObject streamJson(String asset,String file,String audio,JSONArray tracks)throws Exception {
        JSONObject subtitles=new JSONObject();
        if(tracks!=null)for(int i=0;i<Math.min(tracks.length(),32);i++){
            JSONObject t=tracks.optJSONObject(i);if(t==null||"thumbnails".equals(t.optString("kind")))continue;
            String url=NativeMetadata.string(t,"file");if(url.isEmpty())url=NativeMetadata.string(t,"url");
            try{publicHttps(url);}catch(Exception e){continue;}
            if(!"vtt".equalsIgnoreCase(t.optString("format"))&&!new URI(url).getPath().toLowerCase(java.util.Locale.US).endsWith(".vtt"))continue;
            String locale=NativeSubtitles.locale(t);if(locale.isEmpty()||subtitles.has(locale))continue;
            subtitles.put(locale,new JSONObject().put("url",NativeSubtitles.register(url)).put("locale",locale).put("language",locale).put("format","ass"));
        }
        return new JSONObject().put("asset_id",asset).put("media_id",asset).put("audio_locale",audio.equals("dub")?"en-US":"ja-JP")
            .put("streams",new JSONObject().put("adaptive_hls",new JSONObject().put("",new JSONObject().put("url",file).put("hardsub_locale",""))))
            .put("subtitles",subtitles).put("captions",new JSONObject()).put("bifs",new JSONArray()).put("playbackType","ON_DEMAND");
    }
    private static void appendTracks(JSONArray target,JSONArray source){if(source!=null)for(int i=0;i<Math.min(32,source.length());i++)target.put(source.opt(i));}
    static String hardLocale(JSONObject source){String value=NativeMetadata.string(source,"hardsub_locale");if(value.isEmpty())value=NativeMetadata.string(source,"hardsubLocale");try{return value.isEmpty()?"":NativeSubtitles.locale(new JSONObject().put("language",value));}catch(Exception e){return "";}}
    static JSONArray subtitleLocales(String asset)throws Exception {
        String language=BackendBridge.preferredSubtitleLanguage();
        synchronized(CACHE){Cached cached=CACHE.get(asset+":"+language);if(cached!=null){JSONObject data=new JSONObject(cached.json);JSONArray languages=new JSONArray();java.util.Iterator<String> keys=data.getJSONObject("subtitles").keys();while(keys.hasNext())languages.put(keys.next());String hard=data.getJSONObject("streams").getJSONObject("adaptive_hls").getJSONObject("").optString("hardsub_locale");if(!hard.isEmpty())languages.put(hard);return languages;}}
        return new JSONArray();
    }
    static Object model(String cls,JSONObject json)throws Exception {
        Object gson=Class.forName("com.ellation.crunchyroll.api.GsonHolder").getMethod("getInstance").invoke(null);
        return gson.getClass().getMethod("fromJson",String.class,Class.class).invoke(gson,json.toString(),Class.forName(cls));
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    public static Object mapHls(String asset,Object streams,Object offline,String params){
        try {
            if(offline!=null){String name=offline.getClass().getName();if(name.equals("jg.d$a")&&offline.getClass().getField("a").getBoolean(offline)||name.equals("jg.d$b")&&offline.getClass().getField("a").getBoolean(offline))return null;}
            Map<?,?> hls=(Map<?,?>)streams.getClass().getMethod("getHlsStreams").invoke(streams);if(hls==null||hls.isEmpty())return null;
            Object stream=hls.get("");if(stream==null)stream=hls.values().iterator().next();
            String url=(String)stream.getClass().getMethod("getUrl").invoke(stream);publicHttps(url);
            ArrayList<Object> tracks=new ArrayList<>();Map<?,?> subs=(Map<?,?>)streams.getClass().getMethod("getSubtitles").invoke(streams);
            for(Map.Entry<?,?> entry:subs.entrySet()){
                String trackUrl=(String)entry.getValue().getClass().getMethod("getUrl").invoke(entry.getValue());publicHttps(trackUrl);
                tracks.add(Class.forName("bl.d").getConstructor(String.class,String.class).newInstance(entry.getKey().toString(),trackUrl));
            }
            Class protocol=Class.forName("bl.b"),type=Class.forName("com.ellation.crunchyroll.api.cms.model.streams.PlaybackType");
            return Class.forName("bl.c$c").getConstructor(String.class,String.class,protocol,String.class,ArrayList.class,String.class,
                Class.forName("bl.f"),Class.forName("com.ellation.crunchyroll.api.etp.playback.model.SessionState"),String.class,type,int.class)
                .newInstance(asset,"",Enum.valueOf(protocol,"HLS"),url,tracks,null,null,null,params,Enum.valueOf(type,"ON_DEMAND"),386);
        }catch(Exception e){Log.e("APKForgeNative","Native HLS model failure",e);return null;}
    }
    /** Only the retained media data-source factory receives provider playback headers. */
    @SuppressWarnings("unchecked") public static void configureMediaFactory(Object factory){
        try {
            if(!factory.getClass().getName().equals("m5.b$a"))throw new IllegalArgumentException("Unexpected media factory");
            Object properties=factory.getClass().getField("a").get(factory);
            synchronized(properties){
                Map<String,String> headers=(Map<String,String>)properties.getClass().getField("a").get(properties);
                headers.put("Referer",REFERER);headers.put("User-Agent",UA);
                properties.getClass().getField("b").set(properties,null);
            }
        }catch(Exception e){Log.e("APKForgeNative","Media headers unavailable",e);}
    }
    private static boolean embedHost(String url){try{URI u=publicHttps(url);return "megaplay.buzz".equals(u.getHost())&&u.getPath().startsWith("/stream/");}catch(Exception e){return false;}}
    static URI publicHttps(String url)throws Exception {
        URI u=new URI(url);String h=u.getHost();
        if(!"https".equals(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||h==null||h.indexOf('.')<0||h.matches("[0-9.]+")||h.endsWith(".local")||h.endsWith(".localhost")||h.contains(":"))throw new IOException("Invalid public media URL");
        return u;
    }
    static String get(String url,String referer)throws Exception {
        // Follow bounded HTTPS redirects with the same non-secret provider headers.
        for(int redirects=0;redirects<4;redirects++){
            publicHttps(url);HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setInstanceFollowRedirects(false);c.setConnectTimeout(12000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent",UA);
            if(referer!=null)c.setRequestProperty("Referer",referer);
            try {
                int status=c.getResponseCode();if(status>=300&&status<400){url=new URI(url).resolve(c.getHeaderField("Location")).toString();continue;}
                if(status!=200)throw new IOException("Provider HTTP "+status);
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>1048576)throw new IOException("Provider response too large");out.write(b,0,n);}
                    return new String(out.toByteArray(),StandardCharsets.UTF_8);
                }
            }finally{c.disconnect();}
        }throw new IOException("Provider redirect limit");
    }
}
