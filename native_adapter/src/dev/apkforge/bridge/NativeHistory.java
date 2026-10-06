package dev.apkforge.bridge;

import android.net.Uri;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Real profile playheads rendered through the retained history/continue-watching models. */
final class NativeHistory {
    private static final Pattern ASSET=Pattern.compile("ANI([0-9]+)E([0-9]+)(D)?");
    static void save(JSONObject state,JSONObject body)throws Exception {
        JSONObject batch=body.optJSONObject("batch");if(batch==null)batch=new JSONObject().put(body.optString("content_id"),body);
        Iterator<String> keys=batch.keys();List<String> ids=new ArrayList<>();while(keys.hasNext()){String id=keys.next();Matcher valid=ASSET.matcher(id);if(!valid.matches()||batch.optJSONObject(id)==null)throw new BackendBridge.HttpFailure(400,"invalid-playhead-batch");try{if(Integer.parseInt(valid.group(1))<=0||Integer.parseInt(valid.group(2))<=0)throw new NumberFormatException();}catch(NumberFormatException invalid){throw new BackendBridge.HttpFailure(400,"invalid-playhead-batch");}ids.add(id);}if(ids.size()>100)throw new BackendBridge.HttpFailure(400,"playhead-batch-too-large");
        Map<String,JSONObject> staged=new LinkedHashMap<>();
        for(String id:ids){JSONObject update=batch.getJSONObject(id);long watched=watchedAt(update.optString("date_watched")),position=Math.min(86400,Math.max(0,update.optLong("playhead")));JSONObject old=state.optJSONObject(id);if(old!=null&&old.optLong("_watchedAt")>watched)continue;
            JSONObject record=new JSONObject().put("content_id",id).put("playhead",position).put("_watchedAt",watched).put("last_modified",java.time.Instant.ofEpochMilli(watched).toString()).put("fully_watched",false);
            Matcher match=ASSET.matcher(id);match.matches();int source=Integer.parseInt(match.group(1)),number=Integer.parseInt(match.group(2));JSONObject title=NativeFranchises.artwork(source);if(title==null)title=BackendBridge.cachedTitle(source);
            JSONArray eps=title==null?null:title.optJSONArray("episodeList");if(eps!=null)for(int i=0;i<eps.length();i++){JSONObject ep=eps.getJSONObject(i);if(ep.optInt("number")!=number||ep.optInt("_sourceId",title.optInt("anilistId"))!=source)continue;
                JSONObject summary=new JSONObject();for(String key:new String[]{"anilistId","title","poster","banner","synopsis","episodes"})if(title.has(key))summary.put(key,title.get(key));record.put("_title",summary).put("_episode",ep);long duration=ep.optLong("runtimeSeconds");record.put("fully_watched",duration>0&&position>=duration-Math.min(60,duration/10));break;}
            if(!record.has("_episode")&&old!=null&&old.has("_episode")){record.put("_episode",old.get("_episode")).put("_title",old.get("_title"));}
            staged.put(id,record);
        }
        for(Map.Entry<String,JSONObject> entry:staged.entrySet())state.put(entry.getKey(),entry.getValue());
        List<String> ordered=ordered(state);for(int i=1000;i<ordered.size();i++)state.remove(ordered.get(i));
    }
    private static long watchedAt(String date){try{long time=java.time.Instant.parse(date).toEpochMilli();return Math.min(System.currentTimeMillis(),time);}catch(Exception ignored){return System.currentTimeMillis();}}
    private static List<String> ordered(JSONObject state){List<String> ids=new ArrayList<>();Iterator<String> keys=state.keys();while(keys.hasNext()){String id=keys.next();if(ASSET.matcher(id).matches()&&state.optJSONObject(id)!=null)ids.add(id);}ids.sort((a,b)->{JSONObject x=state.optJSONObject(a),y=state.optJSONObject(b);int compared=Long.compare(y.optLong("_watchedAt"),x.optLong("_watchedAt"));return compared!=0?compared:y.optString("last_modified").compareTo(x.optString("last_modified"));});return ids;}
    static JSONObject rows(JSONObject state,Uri uri)throws Exception {
        boolean history=uri.getPath().contains("watch-history");int start=Math.max(0,NativeDiscovery.integer(uri.getQueryParameter("start"),0)),limit=Math.max(1,Math.min(100,NativeDiscovery.integer(uri.getQueryParameter(history?"page_size":"n"),25)));JSONArray data=new JSONArray();Set<String> families=new HashSet<>();int available=0;
        for(String id:ordered(state)){JSONObject record=state.getJSONObject(id);if(!history&&(record.optBoolean("fully_watched")||record.optLong("playhead")<=0))continue;
            try{JSONObject episode=record.has("_episode")?NativeCatalog.episode(record.getJSONObject("_title"),record.getJSONObject("_episode"),id.endsWith("D")):NativeCatalog.route(Uri.parse("https://local/content/v2/cms/episodes/"+id)).getJSONArray("data").getJSONObject(0);
                if(!history&&!families.add(episode.getString("series_id")))continue;if(available++<start)continue;if(data.length()>=limit)break;
                data.put(new JSONObject().put("panel",NativeCatalog.episodePanel(episode)).put("playhead",record.optLong("playhead")).put("fully_watched",record.optBoolean("fully_watched")).put("new",false));
            }catch(Exception unavailable){android.util.Log.w("APKForgeHistory","History entry temporarily unavailable");}
        }
        JSONObject result=BackendBridge.envelope(data);if(available>start+data.length())result.getJSONObject("meta").put("next_page",uri.buildUpon().clearQuery().appendQueryParameter("start",String.valueOf(start+data.length())).appendQueryParameter(history?"page_size":"n",String.valueOf(limit)).build().toString());return result;
    }
    static void delete(JSONObject state,String tail)throws Exception {if(tail.equals("watch-history")){Iterator<String> ids=state.keys();while(ids.hasNext()){ids.next();ids.remove();}return;}for(String id:tail.split(","))if(!ASSET.matcher(id).matches())throw new BackendBridge.HttpFailure(400,"invalid-history-id");for(String id:tail.split(","))state.remove(id);}
}
