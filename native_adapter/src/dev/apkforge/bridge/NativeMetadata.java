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
            synchronized(CACHE){CACHE.put(key,new Entry(value,ttl));}return value;
        }finally{c.disconnect();}
    }
    static void enrich(JSONObject series) {
        try {
            int id=series.getInt("anilistId");
            JSONObject mapping=request("https://api.ani.zip/mappings?anilist_id="+id,null,21600000);
            JSONObject ids=mapping.optJSONObject("mappings");
            // Episode numbering must be attached to the requested AniList entry.
            if(ids==null||ids.optInt("anilist_id")!=id)return;
            mergeEpisodes(series,mapping.optJSONObject("episodes"));
        }catch(Exception e){android.util.Log.w("APKForgeMetadata","Episode metadata unavailable; retaining provider records");}
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
