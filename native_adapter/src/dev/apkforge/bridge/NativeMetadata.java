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
    private static final class Entry {final long expiry;final String data;Entry(JSONObject value,long ttl){data=value.toString();expiry=android.os.SystemClock.elapsedRealtime()+ttl;}}
    static JSONObject graph(String query,JSONObject variables)throws Exception {
        JSONObject result=request("https://graphql.anilist.co",new JSONObject().put("query",query).put("variables",variables),300000);
        if(result.has("errors"))throw new IOException("Metadata query unavailable");
        return result.getJSONObject("data");
    }
    static JSONObject request(String address,JSONObject body,long ttl)throws Exception {
        String key=address+(body==null?"":body.toString());
        synchronized(CACHE){Entry hit=CACHE.get(key);if(hit!=null&&hit.expiry>android.os.SystemClock.elapsedRealtime())return new JSONObject(hit.data);}
        NativePlayback.publicHttps(address);
        HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("User-Agent","APKForge/0.4 (Android)");c.setRequestProperty("Accept","application/json");
        try {
            if(body!=null){c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try(OutputStream out=c.getOutputStream()){out.write(bytes);}}
            if(c.getResponseCode()!=200)throw new IOException("Metadata HTTP "+c.getResponseCode());
            JSONObject value;
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("Metadata response too large");out.write(buffer,0,n);}
                value=new JSONObject(new String(out.toByteArray(),StandardCharsets.UTF_8));
            }
            synchronized(CACHE){CACHE.put(key,new Entry(value,ttl));trimCache();}return value;
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
        for(int i=0;i<records.length();i++){JSONObject r=records.getJSONObject(i);int id=r.optInt("anilistId");if(id<=0)continue;java.util.List<JSONObject> list=grouped.get(id);if(list==null){list=new java.util.ArrayList<>();grouped.put(id,list);}list.add(r);}
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
