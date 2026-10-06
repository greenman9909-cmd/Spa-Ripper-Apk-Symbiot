package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

/** Public metadata only. Never participates in media authorization. */
final class NativeMetadata {
    private static final Map<String,Entry> CACHE=new LinkedHashMap<String,Entry>(32,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,NativeMetadata.Entry> e){return size()>96;}
    };
    private static final class Entry {final long expiry,staleUntil;final String data;Entry(JSONObject value,long ttl){this(value,ttl,NativePublicStore.MAX_AGE);}Entry(JSONObject value,long ttl,long stale){data=value.toString();expiry=android.os.SystemClock.elapsedRealtime()+ttl;staleUntil=android.os.SystemClock.elapsedRealtime()+stale;}}
    private static final Object[] REQUEST_LOCKS=new Object[32];static{for(int i=0;i<REQUEST_LOCKS.length;i++)REQUEST_LOCKS[i]=new Object();}
    private static final Map<String,Long> COOLDOWNS=new java.util.HashMap<>();
    interface Fetch {JSONObject get()throws Exception;}
    static final class ProviderFailure extends IOException {final int status;final long retryMillis;ProviderFailure(int s,long retry){super("Metadata HTTP "+s);status=s;retryMillis=retry;}}
    static long retryMillis(String header,long wallNow){
        try{return Math.max(1,Math.min(900,Long.parseLong(header.trim())))*1000;}catch(Exception ignored){}
        try{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",java.util.Locale.US);return Math.max(1000,Math.min(900000,f.parse(header).getTime()-wallNow));}catch(Exception ignored){return 60000;}
    }
    static JSONObject graph(String query,JSONObject variables)throws Exception {
        JSONObject result=request("https://graphql.anilist.co",new JSONObject().put("query",query).put("variables",variables),120000);
        if(result.has("errors"))throw new IOException("Metadata query unavailable");
        return result.getJSONObject("data");
    }
    static JSONObject request(String address,JSONObject body,long ttl)throws Exception {
        String key=address+(body==null?"":body.toString());
        NativePlayback.publicHttps(address);
        return cachedRequest(address,key,ttl,()->fetch(address,body));
    }
    static JSONObject cachedRequest(String address,String key,long ttl,Fetch fetch)throws Exception {
        synchronized(REQUEST_LOCKS[(key.hashCode()&Integer.MAX_VALUE)%REQUEST_LOCKS.length]){
            long now=android.os.SystemClock.elapsedRealtime();Entry hit;synchronized(CACHE){hit=CACHE.get(key);}
            if(hit==null){JSONObject disk=NativePublicStore.read(NativePublicStore.directory(),key);if(disk!=null){long age=System.currentTimeMillis()-disk.getLong("savedAt");hit=new Entry(disk.getJSONObject("value"),Math.max(0,ttl-age),Math.max(0,NativePublicStore.MAX_AGE-age));synchronized(CACHE){CACHE.put(key,hit);trimCache();}}}
            if(hit!=null&&hit.expiry>now)return new JSONObject(hit.data);
            String host=new java.net.URI(address).getHost();long until;synchronized(COOLDOWNS){until=COOLDOWNS.containsKey(host)?COOLDOWNS.get(host):0;}
            if(until>now){if(hit!=null&&hit.staleUntil>now)return new JSONObject(hit.data);throw new ProviderFailure(429,until-now);}
            try{JSONObject value=fetch.get();synchronized(CACHE){CACHE.put(key,new Entry(value,ttl));trimCache();}NativePublicStore.write(NativePublicStore.directory(),key,value);return value;}
            catch(Exception failure){boolean temporary=failure instanceof IOException;
                if(failure instanceof ProviderFailure){ProviderFailure p=(ProviderFailure)failure;temporary=p.status==429||p.status==408||p.status>=500;if(p.status==429||p.status==503)synchronized(COOLDOWNS){COOLDOWNS.put(host,android.os.SystemClock.elapsedRealtime()+p.retryMillis);}}
                if(temporary&&hit!=null&&hit.staleUntil>android.os.SystemClock.elapsedRealtime()){android.util.Log.w("APKForgeMetadata","Provider unavailable; retaining last valid catalog");return new JSONObject(hit.data);}throw failure;
            }
        }
    }
    private static JSONObject fetch(String address,JSONObject body)throws Exception {
        HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","APKForge/0.4 (Android)");c.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            int status=c.getResponseCode();if(status!=200)throw new ProviderFailure(status,retryMillis(c.getHeaderField("Retry-After"),System.currentTimeMillis()));
            JSONObject value;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("Metadata response too large");out.write(buffer,0,n);}
                value=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
            if(address.equals("https://graphql.anilist.co")&&(value.has("errors")||value.optJSONObject("data")==null))throw new IOException("Metadata query unavailable");return value;
        }finally{c.disconnect();}
    }
    static void trimCache(){
        // String character budgets also bound large episode mappings, not only entry count.
        long chars=0;for(Entry e:CACHE.values())chars+=e.data.length();
        java.util.Iterator<Entry> oldest=CACHE.values().iterator();while(chars>3*1024*1024&&oldest.hasNext()){chars-=oldest.next().data.length();oldest.remove();}
    }
    static void enrich(JSONObject series) {
        try{artwork(new JSONArray().put(series));}catch(Exception unavailable){android.util.Log.w("APKForgeMetadata","High resolution artwork unavailable; retaining catalog artwork");}
        try {
            int id=series.getInt("anilistId");
            JSONObject mapping=request("https://api.ani.zip/mappings?anilist_id="+id,null,21600000);
            JSONObject ids=mapping.optJSONObject("mappings");
            // Episode numbering must be attached to the requested AniList entry.
            if(ids==null||ids.optInt("anilist_id")!=id)return;
            mergeEpisodes(series,mapping.optJSONObject("episodes"));
            NativeSeasons.apply(series,mapping.optJSONObject("episodes"));
        }catch(Exception e){android.util.Log.w("APKForgeMetadata","Episode metadata unavailable; retaining provider records");}
    }
    static void invalidateDiscovery(){synchronized(CACHE){java.util.Iterator<String> keys=CACHE.keySet().iterator();while(keys.hasNext())if(keys.next().startsWith("https://graphql.anilist.co"))keys.remove();}}
    static void artwork(JSONArray records)throws Exception {
        java.util.LinkedHashMap<Integer,java.util.List<JSONObject>> grouped=new java.util.LinkedHashMap<>();
        for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);int id=r.optInt("anilistId");if(id<=0||r.optBoolean("metadataOnly")&&!string(r,"poster").isEmpty())continue;java.util.List<JSONObject> list=grouped.get(id);if(list==null){list=new java.util.ArrayList<>();grouped.put(id,list);}list.add(r);}
        java.util.List<Integer> ids=new java.util.ArrayList<>(grouped.keySet());
        for(int start=0;start<ids.size();start+=50){JSONArray batch=new JSONArray();for(int i=start;i<Math.min(start+50,ids.size());i++)batch.put(ids.get(i));
            JSONArray media=graph("query($ids:[Int]){Page(perPage:50){media(id_in:$ids,type:ANIME){id coverImage{extraLarge large}bannerImage}}}",new JSONObject().put("ids",batch)).getJSONObject("Page").getJSONArray("media");
            for(int i=0;i<media.length();i++){JSONObject source=media.getJSONObject(i);java.util.List<JSONObject> targets=grouped.get(source.getInt("id"));if(targets==null)continue;String cover=NativeDiscovery.cover(source),banner=string(source,"bannerImage");
                for(JSONObject target:targets){if(!cover.isEmpty())target.put("poster",cover);if(!banner.isEmpty())target.put("banner",banner);}}
        }
    }
    static void mergeEpisodes(JSONObject series,JSONObject metadata)throws Exception {
        JSONArray episodes=series.optJSONArray("episodeList");if(episodes==null||metadata==null)return;
        for(int i=0;i<episodes.length();i++){
            JSONObject target=episodes.getJSONObject(i),info=metadata.optJSONObject(String.valueOf(target.getInt("number")));if(info==null)continue;
            String image=string(info,"image");
            if(string(target,"thumbnail").isEmpty()&&!image.isEmpty())try{NativePlayback.publicHttps(image);target.put("thumbnail",image);}catch(Exception invalid){}
            JSONObject titles=info.optJSONObject("title");
            if(string(target,"title").isEmpty()&&titles!=null){String name=string(titles,"en");if(name.isEmpty())name=string(titles,"x-jat");if(!name.isEmpty())target.put("title",name.replace('`','\''));}
            if(!string(info,"overview").isEmpty())target.put("description",info.getString("overview"));
            if(!string(info,"airDateUtc").isEmpty())target.put("aired",info.getString("airDateUtc"));
        }
    }
    static String string(JSONObject object,String field){return object.isNull(field)?"":object.optString(field,"");}
}
