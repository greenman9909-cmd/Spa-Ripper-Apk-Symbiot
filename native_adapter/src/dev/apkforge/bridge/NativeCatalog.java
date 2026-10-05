package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Converts provider records into the original screen models. */
final class NativeCatalog {
    private static final Pattern ID=Pattern.compile("ANI([0-9]+)(?:S([0-9]+)|E([0-9]+)(D)?)?");
    static boolean handles(String path){return path.contains("/cms/series/")||path.contains("/cms/seasons/")||path.contains("/cms/episodes/")||path.contains("/up_next/");}
    static JSONObject route(Uri uri)throws Exception {
        String path=uri.getPath();Matcher match=ID.matcher(path);
        if(!match.find())throw new BackendBridge.HttpFailure(404,"title-not-found");
        int number=Integer.parseInt(match.group(1));JSONObject title=BackendBridge.series(number);
        JSONArray episodes=title.optJSONArray("episodeList");if(episodes==null)episodes=new JSONArray();
        if(path.endsWith("/seasons")){
            JSONObject season=new JSONObject().put("id","ANI"+number+"S1").put("series_id","ANI"+number).put("channel_id","crunchyroll")
                .put("title",title.optString("title")).put("season_number","1").put("season_display_number","1").put("number_of_episodes",episodes.length());
            return BackendBridge.envelope(new JSONArray().put(season)).put("meta",new JSONObject().put("versions_considered",false));
        }
        if(path.endsWith("/episodes")){
            JSONArray data=new JSONArray();for(int i=0;i<episodes.length();i++)data.put(episode(title,episodes.getJSONObject(i)));
            return BackendBridge.envelope(data);
        }
        if(path.contains("/cms/episodes/")||path.contains("/up_next/")){
            int wanted=match.group(3)==null?1:Integer.parseInt(match.group(3));
            boolean dubbed=match.group(4)!=null;
            if(path.contains("/up_next/")&&match.group(3)!=null)wanted++;
            for(int i=0;i<episodes.length();i++){
                JSONObject e=episodes.getJSONObject(i);if(e.getInt("number")!=wanted)continue;
                JSONObject nativeEpisode=episode(title,e,dubbed);
                if(path.contains("/up_next/")){
                    JSONObject panel=episodePanel(nativeEpisode);
                    return BackendBridge.envelope(new JSONArray().put(new JSONObject().put("panel",panel).put("playhead",0).put("never_watched",true).put("fully_watched",false)));
                }
                return BackendBridge.envelope(new JSONArray().put(nativeEpisode));
            }
            if(path.contains("/up_next/"))return BackendBridge.envelope(new JSONArray());
            throw new BackendBridge.HttpFailure(404,"episode-not-found");
        }
        JSONObject nativeSeries=BackendBridge.panel(title);JSONObject metadata=nativeSeries.getJSONObject("series_metadata");
        java.util.Iterator<String> keys=metadata.keys();while(keys.hasNext()){String k=keys.next();nativeSeries.put(k,metadata.get(k));}
        nativeSeries.put("content_provider","ani.pm");return BackendBridge.envelope(new JSONArray().put(nativeSeries));
    }
    static JSONObject episode(JSONObject title,JSONObject episode)throws Exception {return episode(title,episode,false);}
    static JSONObject watchlistPanel(JSONObject title)throws Exception {
        JSONArray episodes=title.optJSONArray("episodeList");
        if(episodes==null||episodes.length()==0)return BackendBridge.panel(title);
        JSONObject first=episode(title,episodes.getJSONObject(0));
        return episodePanel(first);
    }
    static JSONObject episodePanel(JSONObject episode)throws Exception {
        return new JSONObject().put("id",episode.getString("id")).put("type","episode")
            .put("title",episode.getString("title")).put("images",episode.getJSONObject("images"))
            .put("streams_link",episode.getString("streams_link")).put("episode_metadata",episode);
    }
    private static JSONObject episode(JSONObject title,JSONObject episode,boolean dubbed)throws Exception {
        int id=title.getInt("anilistId"),n=episode.getInt("number");JSONObject available=episode.optJSONObject("available");if(available==null)available=new JSONObject();
        dubbed=dubbed||(!available.optBoolean("sub")&&available.optBoolean("dub"));
        if((dubbed&&!available.optBoolean("dub"))||(!dubbed&&!available.optBoolean("sub")))throw new BackendBridge.HttpFailure(404,"audio-version-unavailable");
        String image=episode.isNull("thumbnail")?title.optString("poster"):episode.optString("thumbnail");
        JSONObject source=new JSONObject().put("source",image).put("width",640).put("height",360);
        JSONObject images=new JSONObject().put("thumbnail",new JSONArray().put(new JSONArray().put(source)));
        String name=episode.isNull("title")?"Episode "+n:episode.optString("title","Episode "+n);
        String base="ANI"+id+"E"+n;JSONArray versions=new JSONArray();
        if(available.optBoolean("sub"))versions.put(version(base,id,"ja-JP",true));
        if(available.optBoolean("dub"))versions.put(version(base+"D",id,"en-US",!available.optBoolean("sub")));
        String asset=dubbed?base+"D":base;
        return new JSONObject().put("id",asset).put("title",name).put("description",episode.optString("description","")).put("images",images)
            .put("series_id","ANI"+id).put("series_title",title.optString("title")).put("season_id","ANI"+id+"S1")
            .put("season_title",title.optString("title")).put("season_number","1").put("season_display_number","1")
            .put("episode",String.valueOf(n)).put("episode_number",String.valueOf(n)).put("duration_ms",episode.optLong("runtimeSeconds")*1000)
            .put("is_subbed",!dubbed).put("is_dubbed",dubbed).put("is_premium_only",false)
            // Provider marks this audio available now. These are adapter access
            // dates, not invented broadcast dates. Missing dates make the
            // retained availability monitor continually reload the episode.
            .put("available_date","1970-01-01T00:00:00Z").put("free_available_date","1970-01-01T00:00:00Z")
            .put("premium_available_date","1970-01-01T00:00:00Z")
            .put("media_type","episode").put("channel_id","crunchyroll").put("audio_locale",dubbed?"en-US":"ja-JP")
            .put("subtitle_locales",NativePlayback.subtitleLocales(asset)).put("versions",versions).put("available_offline",false)
            .put("maturity_ratings",new JSONArray()).put("tenant_categories",new JSONArray())
            .put("streams_link","/apkforge/playback/"+asset);
    }
    private static JSONObject version(String asset,int series,String locale,boolean original)throws Exception {
        return new JSONObject().put("guid",asset).put("season_guid","ANI"+series+"S1").put("audio_locale",locale)
            .put("original",original).put("variant","").put("is_premium_only",false);
    }
}
