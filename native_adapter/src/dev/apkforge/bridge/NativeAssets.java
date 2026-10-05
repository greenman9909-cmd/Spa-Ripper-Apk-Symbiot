package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

/** Catalog images selected through the original native avatar/background picker. */
public final class NativeAssets {
    public static String avatar(String id){
        if("default.png".equals(id))return "https://ani.pm/api/anime/cover?anilistId=21";
        return cover(id);
    }
    public static String wallpaper(String id){
        if("default.png".equals(id))return "https://ani.pm/api/anime/cover?anilistId=154587";
        return cover(id);
    }
    private static String cover(String id){
        return id!=null&&id.matches("ani-cover-[0-9]{1,9}")?"https://ani.pm/api/anime/cover?anilistId="+id.substring(10):null;
    }
    static JSONObject catalog()throws Exception {
        JSONArray records=BackendBridge.api("/top?range=week&adult=0&limit=20").getJSONArray("data"),assets=new JSONArray();
        assets.put(new JSONObject().put("id","default.png").put("title","Default"));
        for(int i=0;i<records.length();i++){JSONObject item=records.getJSONObject(i);if(item.optBoolean("adult")||item.optInt("anilistId")<1)continue;
            assets.put(new JSONObject().put("id","ani-cover-"+item.getInt("anilistId")).put("title",item.optString("title")));}
        return new JSONObject().put("items",new JSONArray().put(new JSONObject().put("title","Anime Artwork").put("assets",assets)));
    }
}
