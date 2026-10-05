package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.TreeSet;

/** Episode groupings never change the provider's absolute playback number. */
final class NativeSeasons {
    static void apply(JSONObject title,JSONObject mapping)throws Exception {
        JSONArray episodes=title.optJSONArray("episodeList");if(episodes==null)return;
        boolean blackClover=title.optInt("anilistId")==97940;
        for(int i=0;i<episodes.length();i++){
            JSONObject episode=episodes.getJSONObject(i),info=mapping==null?null:mapping.optJSONObject(String.valueOf(episode.getInt("number")));
            int season=info==null?1:Math.max(1,info.optInt("seasonNumber",1));
            // English release divisions: 1–51, 52–102, 103–154, 155–170.
            // Bound to the original entry; no invented episodes or future continuation.
            int n=episode.getInt("number");if(blackClover&&n<=170)season=n<=51?1:n<=102?2:n<=154?3:4;
            episode.put("seasonNumber",season);
        }
        title.put("_seasonNumbers",new JSONArray(numbers(title)));
    }
    static int number(JSONObject episode){return Math.max(1,episode.optInt("seasonNumber",1));}
    static TreeSet<Integer> numbers(JSONObject title){
        TreeSet<Integer> numbers=new TreeSet<>();JSONArray episodes=title.optJSONArray("episodeList");
        if(episodes!=null)for(int i=0;i<episodes.length();i++){JSONObject e=episodes.optJSONObject(i);if(e!=null)numbers.add(number(e));}
        else{JSONArray stored=title.optJSONArray("_seasonNumbers");if(stored!=null)for(int i=0;i<stored.length();i++)if(stored.optInt(i)>0)numbers.add(stored.optInt(i));}
        if(numbers.isEmpty())numbers.add(1);return numbers;
    }
}
