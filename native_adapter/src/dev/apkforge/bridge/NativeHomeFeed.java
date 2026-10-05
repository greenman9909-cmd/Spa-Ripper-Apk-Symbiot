package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/** Provider collections rendered by the retained native Home feed. */
final class NativeHomeFeed {
    private static JSONArray snapshot;
    private static long expires;
    private static final String[][] COLLECTIONS={
        {"popular","Popular This Week","range=week"},
        {"trending","Trending Today","range=today"},
        {"monthly","Popular This Month","range=month"},
        {"classics","All-Time Favorites","range=all"},
        {"movies","Anime Movies","range=all&format=MOVIE"},
        {"action","Action","range=all&genre=Action"},
        {"adventure","Adventure","range=all&genre=Adventure"},
        {"fantasy","Fantasy","range=all&genre=Fantasy"}
    };
    static String query(String id)throws BackendBridge.HttpFailure {
        for(String[] c:COLLECTIONS)if(c[0].equals(id))return "/top?"+c[2]+"&adult=0&limit=100";
        throw new BackendBridge.HttpFailure(404,"collection-not-found");
    }
    static JSONObject collection(String id,String title,JSONArray records)throws Exception {
        JSONArray ids=new JSONArray();LinkedHashSet<Integer> unique=new LinkedHashSet<>();
        for(int i=0;i<records.length()&&unique.size()<20;i++){
            JSONObject record=records.getJSONObject(i);int key=record.optInt("anilistId");
            if(key>0&&!record.optBoolean("adult")&&unique.add(key))ids.put("ANI"+key);
        }
        return new JSONObject().put("id","ani-"+id).put("resource_type","CURATED_COLLECTION")
            .put("response_type","SERIES").put("title",title).put("ids",ids)
            .put("link","/content/v2/discover/apkforge-"+id);
    }
    static synchronized JSONObject home(int start,int limit)throws Exception {
        // Hold subsequent pages in the same feed even when its freshness window expires.
        // Refreshing in the middle of a scroll can reorder rows or repeat the hero.
        if(snapshot==null||(start<=0&&expires<=android.os.SystemClock.elapsedRealtime())){
            snapshot=load();expires=android.os.SystemClock.elapsedRealtime()+600000;
        }
        return page(snapshot,start,limit);
    }
    static JSONObject page(JSONArray feed,int start,int limit)throws Exception {
        int offset=Math.max(0,start),count=Math.max(1,Math.min(100,limit));
        JSONArray rows=new JSONArray();
        for(int i=offset;i<feed.length()&&i-offset<count;i++)rows.put(feed.get(i));
        return BackendBridge.envelope(rows).put("total",feed.length());
    }
    private static JSONArray load()throws Exception {
        ExecutorService pool=Executors.newFixedThreadPool(4);
        List<Future<JSONArray>> tasks=new ArrayList<>();
        try{
            for(String[] c:COLLECTIONS){final String id=c[0];tasks.add(pool.submit(()->BackendBridge.api(query(id).replace("limit=100","limit=20")).getJSONArray("data")));}
            JSONArray feed=new JSONArray();
            for(int i=0;i<COLLECTIONS.length;i++){
                JSONArray records;
                try{records=tasks.get(i).get(40,TimeUnit.SECONDS);}catch(ExecutionException|TimeoutException e){continue;}
                BackendBridge.cache(records);JSONObject row=collection(COLLECTIONS[i][0],COLLECTIONS[i][1],records);
                if(row.getJSONArray("ids").length()==0)continue;
                if(feed.length()==0){
                    String heroId=row.getJSONArray("ids").getString(0);int key=Integer.parseInt(heroId.substring(3));
                    for(int n=0;n<records.length();n++)if(records.getJSONObject(n).optInt("anilistId")==key){
                        JSONObject hero=records.getJSONObject(n);
                        feed.put(new JSONObject().put("id","ani-hero").put("resource_type","PANEL").put("response_type","SERIES")
                            .put("display_type","HERO").put("panel",BackendBridge.panel(hero)).put("title",hero.optString("title")));break;
                    }
                }
                feed.put(row);
            }
            if(feed.length()==0)throw new BackendBridge.HttpFailure(503,"catalog-unavailable");
            return feed;
        }finally{pool.shutdownNow();}
    }
}
