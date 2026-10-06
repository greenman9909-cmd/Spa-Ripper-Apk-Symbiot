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
    static java.util.TreeSet<Integer> seasonNumbers(JSONObject title){return NativeSeasons.numbers(title);}
    static JSONObject route(Uri uri)throws Exception {
        String path=uri.getPath();Matcher match=ID.matcher(path);
        if(!match.find())throw new BackendBridge.HttpFailure(404,"title-not-found");
        int number=Integer.parseInt(match.group(1));JSONObject title=NativeFranchises.series(number);int root=title.getInt("anilistId");
        JSONArray episodes=title.optJSONArray("episodeList");if(episodes==null)episodes=new JSONArray();
        if(path.endsWith("/seasons")){
            JSONArray seasons=new JSONArray();for(int group:seasonNumbers(title)){int count=0;for(int i=0;i<episodes.length();i++)if(NativeSeasons.number(episodes.getJSONObject(i))==group)count++;
                seasons.put(new JSONObject().put("id","ANI"+root+"S"+group).put("series_id","ANI"+root).put("channel_id","crunchyroll")
                    .put("title",title.optString("title")+" — Season "+group).put("season_number",String.valueOf(group)).put("season_display_number",String.valueOf(group)).put("number_of_episodes",count));}
            return BackendBridge.envelope(seasons).put("meta",new JSONObject().put("versions_considered",false));
        }
        if(path.endsWith("/episodes")){
            int group=match.group(2)==null?1:Integer.parseInt(match.group(2));JSONArray data=new JSONArray();for(int i=0;i<episodes.length();i++){JSONObject e=episodes.getJSONObject(i);if(NativeSeasons.number(e)==group)data.put(episode(title,e));}
            return BackendBridge.envelope(data);
        }
        if(path.contains("/cms/episodes/")||path.contains("/up_next/")){
            int wanted=match.group(3)==null?1:Integer.parseInt(match.group(3));
            boolean dubbed=match.group(4)!=null;
            if(path.contains("/up_next/")&&match.group(3)!=null){
                for(int i=0;i<episodes.length();i++){JSONObject current=episodes.getJSONObject(i);if(current.getInt("number")!=wanted||current.optInt("_sourceId",root)!=number)continue;
                    if(i+1>=episodes.length())return BackendBridge.envelope(new JSONArray());
                    JSONObject next=episode(title,episodes.getJSONObject(i+1),dubbed);return BackendBridge.envelope(new JSONArray().put(new JSONObject().put("panel",episodePanel(next)).put("playhead",0).put("never_watched",true).put("fully_watched",false)));
                }return BackendBridge.envelope(new JSONArray());
            }
            for(int i=0;i<episodes.length();i++){
                JSONObject e=episodes.getJSONObject(i);if(e.getInt("number")!=wanted||e.optInt("_sourceId",root)!=number)continue;
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
    static JSONObject episode(JSONObject title,JSONObject episode,boolean dubbed)throws Exception {
        int id=title.getInt("anilistId"),sourceId=episode.optInt("_sourceId",id),n=episode.getInt("number"),season=NativeSeasons.number(episode);JSONObject available=episode.optJSONObject("available");if(available==null)available=new JSONObject();
        dubbed=dubbed||(!available.optBoolean("sub")&&available.optBoolean("dub"));
        if((dubbed&&!available.optBoolean("dub"))||(!dubbed&&!available.optBoolean("sub")))throw new BackendBridge.HttpFailure(404,"audio-version-unavailable");
        String image=episode.isNull("thumbnail")?title.optString("poster"):episode.optString("thumbnail");
        JSONObject source=new JSONObject().put("source",NativeArtwork.wide(image)).put("width",1280).put("height",720);
        JSONObject images=new JSONObject().put("thumbnail",new JSONArray().put(new JSONArray().put(source)));
        String name=episode.isNull("title")?"Episode "+n:episode.optString("title","Episode "+n);
        String base="ANI"+sourceId+"E"+n;JSONArray versions=new JSONArray();
        if(available.optBoolean("sub"))versions.put(version(base,id,season,"ja-JP",true));
        if(available.optBoolean("dub"))versions.put(version(base+"D",id,season,"en-US",!available.optBoolean("sub")));
        String asset=dubbed?base+"D":base;
        return new JSONObject().put("id",asset).put("title",name).put("description",episode.optString("description","")).put("images",images)
            .put("series_id","ANI"+id).put("series_title",title.optString("title")).put("season_id","ANI"+id+"S"+season)
            .put("season_title",title.optString("title")+" — Season "+season).put("season_number",String.valueOf(season)).put("season_display_number",String.valueOf(season))
            .put("episode",String.valueOf(n)).put("episode_number",String.valueOf(n)).put("duration_ms",episode.optLong("runtimeSeconds")*1000)
            .put("is_subbed",!dubbed).put("is_dubbed",dubbed).put("is_premium_only",false)
            // Provider marks this audio available now. These are adapter access
            // dates, not invented broadcast dates. Missing dates make the
            // retained availability monitor continually reload the episode.
            .put("available_date","1970-01-01T00:00:00Z").put("free_available_date","1970-01-01T00:00:00Z")
            .put("premium_available_date","1970-01-01T00:00:00Z")
            .put("media_type","episode").put("channel_id","crunchyroll").put("audio_locale",dubbed?"en-US":"ja-JP")
            .put("subtitle_locales",NativePlayback.subtitleLocales(asset)).put("versions",versions).put("available_offline",true)
            .put("maturity_ratings",new JSONArray()).put("tenant_categories",new JSONArray())
            .put("streams_link","/apkforge/playback/"+asset);
    }
    private static JSONObject version(String asset,int series,int season,String locale,boolean original)throws Exception {
        return new JSONObject().put("guid",asset).put("season_guid","ANI"+series+"S"+season).put("audio_locale",locale)
            .put("original",original).put("variant","").put("is_premium_only",false);
    }
}
