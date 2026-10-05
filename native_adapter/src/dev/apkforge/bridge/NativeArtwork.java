package dev.apkforge.bridge;

import org.json.JSONObject;
import org.json.JSONArray;

/** Maps replacement IDs at the original CDN URL boundary; preserves original image views. */
public final class NativeArtwork {
    public static String url(String id,Object imageType){
        if(id==null||!id.matches("ANI[0-9]+(?:E[0-9]+D?)?"))return null;
        try{
            String type=(String)imageType.getClass().getMethod("getRawValue").invoke(imageType);
            // Original show-logo components fall back to their original title typography.
            if(type.toLowerCase(java.util.Locale.US).contains("logo"))return "";
            int end=id.indexOf('E');int number=Integer.parseInt(id.substring(3,end<0?id.length():end));
            JSONObject title=BackendBridge.cachedTitle(number);
            if(title!=null){
                if(end>0){String raw=id.substring(end+1).replace("D","");JSONArray episodes=title.optJSONArray("episodeList");
                    if(episodes!=null)for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.getJSONObject(i);if(ep.optInt("number")==Integer.parseInt(raw)){String photo=NativeMetadata.string(ep,"thumbnail");if(!photo.isEmpty())return photo;}}}
                String banner=NativeMetadata.string(title,"banner"),poster=NativeMetadata.string(title,"poster");
                if(!type.toLowerCase(java.util.Locale.US).contains("tall")&&!banner.isEmpty())return banner;
                if(!poster.isEmpty())return poster;
            }
            return "https://ani.pm/api/anime/cover?anilistId="+number;
        }catch(Exception unavailable){return "";}
    }
}
