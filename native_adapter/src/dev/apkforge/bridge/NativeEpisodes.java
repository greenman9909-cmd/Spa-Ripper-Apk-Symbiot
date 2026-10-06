package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.TreeMap;

/** Secondary episode availability from the same API used by native playback. */
final class NativeEpisodes {
    static final String BASE="https://anivexaapi-aniko2.hf.space/api";
    static JSONObject load(int id,JSONObject summary)throws Exception {
        // The provider's no-MAL branch guesses audio availability. Never use it.
        if(summary.optInt("malId")<=0)summary=NativeDiscovery.detail(id);
        return map(id,summary,NativeMetadata.request(BASE+"/episodes/"+id,null,120000));
    }
    static JSONObject map(int id,JSONObject summary,JSONObject response)throws Exception {
        JSONObject meta=response.optJSONObject("meta"),groups=response.optJSONObject("episodes");
        if(id<=0||summary.optInt("anilistId")!=id||summary.optInt("malId")<=0||meta==null||meta.optInt("malId")!=summary.optInt("malId")||groups==null)
            throw new IOException("Episode identity unavailable");
        TreeMap<Integer,JSONObject> episodes=new TreeMap<>();int sub=0,dub=0;
        for(String audio:new String[]{"sub","dub"}){
            JSONArray records=groups.optJSONArray(audio);
            if(records==null||records.length()>6000)throw new IOException("Invalid episode list");
            for(int i=0;i<records.length();i++){
                JSONObject record=records.optJSONObject(i);if(record==null)throw new IOException("Invalid episode record");
                double raw=record.optDouble("number",-1);int number=(int)raw;
                if(number<=0||number>100000||raw!=number||!record.optString("id").equals("watch/anikoto/"+id+"/"+audio+"/anikoto-"+number)||!record.optString("audio").equals(audio))
                    throw new IOException("Episode source mismatch");
                if(!Boolean.TRUE.equals(record.opt(audio.equals("sub")?"hasSub":"hasDub")))continue;
                JSONObject episode=episodes.get(number);
                if(episode==null){
                    episode=new JSONObject().put("number",number).put("available",new JSONObject());
                    String name=NativeMetadata.string(record,"title");episode.put("title",name.isEmpty()?"Episode "+number:name);
                    episode.put("description",NativeMetadata.string(record,"description")).put("airDate",NativeMetadata.string(record,"airDate"));
                    double duration=record.optDouble("duration",0);episode.put("runtimeSeconds",Double.isNaN(duration)||Double.isInfinite(duration)?0:Math.max(0,Math.min(86400,duration)));
                    String image=NativeMetadata.string(record,"image");try{NativePlayback.publicHttps(image);episode.put("thumbnail",image);}catch(Exception absent){episode.put("thumbnail",JSONObject.NULL);}
                    episodes.put(number,episode);
                }
                JSONObject available=episode.getJSONObject("available");
                if(!available.optBoolean(audio)){available.put(audio,true);if(audio.equals("sub"))sub++;else dub++;}
            }
        }
        if(episodes.isEmpty())throw new IOException("Episode availability unavailable");
        JSONObject title=new JSONObject(summary.toString());
        title.remove("_availabilityUnknown");title.remove("metadataOnly");
        title.put("synopsis",title.optString("synopsis").replace("\n\nEpisodes are temporarily unavailable. Please try again.",""));
        return title.put("episodeList",new JSONArray(episodes.values())).put("episodes",new JSONObject().put("total",episodes.size()).put("sub",sub).put("dub",dub)).put("_episodeProvider","anivexa");
    }
}
