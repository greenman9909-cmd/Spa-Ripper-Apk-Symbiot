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
    private static JSONArray refreshed;
    private static long expires;
    private static FutureTask<JSONArray> loading;
    private static final Object LOCK=new Object();
    private static final ExecutorService REFRESH=Executors.newSingleThreadExecutor(task->{Thread thread=new Thread(task,"APKForgeHomeRefresh");thread.setDaemon(true);return thread;});
    private static final int ROW_SIZE=14;
    private static final long FRESHNESS_MS=120000;
    private static final String[][] COLLECTIONS={
        {"popular","Popular This Week","range=week"},
        {"trending","Trending Today","range=today"},
        {"monthly","Popular This Month","range=month"},
        {"classics","All-Time Favorites","range=all"},
        {"movies","Anime Movies","range=all&format=MOVIE"},
        {"action","Action","range=all&genre=Action"},
        {"adventure","Adventure","range=all&genre=Adventure"},
        {"fantasy","Fantasy","range=all&genre=Fantasy"},
        {"airing","Currently Airing",""},
        {"season","New This Season",""},
        {"comedy","Comedy","range=all&genre=Comedy"},
        {"romance","Romance","range=all&genre=Romance"},
        {"drama","Drama","range=all&genre=Drama"},
        {"scifi","Science Fiction","range=all&genre=Sci-Fi"},
        {"mystery","Mystery","range=all&genre=Mystery"},
        {"sports","Sports","range=all&genre=Sports"}
    };
    static String query(String id)throws BackendBridge.HttpFailure {
        for(String[] c:COLLECTIONS)if(c[0].equals(id))return "/top?"+c[2]+"&adult=0&limit=100";
        throw new BackendBridge.HttpFailure(404,"collection-not-found");
    }
    static String seasonalQuery(String id)throws BackendBridge.HttpFailure {
        if("current".equals(id))return "/top?range=today&adult=0&limit=100";
        throw new BackendBridge.HttpFailure(404,"seasonal-tag-not-found");
    }
    static JSONObject seasonalTags()throws Exception {
        // The provider exposes live airing state, not a reliable historical calendar.
        JSONArray data=new JSONArray().put(new JSONObject().put("id","current")
            .put("localization",new JSONObject().put("title","Currently Airing")));
        return BackendBridge.envelope(data);
    }
    static JSONObject collection(String id,String title,JSONArray records)throws Exception {
        JSONArray ids=new JSONArray();LinkedHashSet<Integer> unique=new LinkedHashSet<>();
        for(int i=0;i<records.length()&&unique.size()<32;i++){
            JSONObject record=records.getJSONObject(i);int key=record.optInt("anilistId");
            if(key>0&&!record.optBoolean("adult")&&unique.add(key))ids.put("ANI"+key);
        }
        return new JSONObject().put("id","ani-"+id).put("resource_type","CURATED_COLLECTION")
            .put("response_type","SERIES").put("title",title).put("ids",ids)
            .put("link","/content/v2/discover/apkforge-"+id);
    }
    static JSONObject home(int start,int limit)throws Exception {
        FutureTask<JSONArray> task;
        synchronized(LOCK){
            // Install a completed refresh only when a new feed starts. Later pages
            // always belong to the active snapshot, including while refresh is running.
            if(start<=0&&refreshed!=null){snapshot=refreshed;refreshed=null;}
            if(snapshot!=null&&(start>0||expires>android.os.SystemClock.elapsedRealtime()))return page(snapshot,start,limit);
            if(loading==null){
                loading=new FutureTask<>(()->{
                    try{
                        JSONArray result=load();
                        synchronized(LOCK){if(!acceptRefresh(snapshot,result))throw new BackendBridge.HttpFailure(503,"partial-catalog-refresh");refreshed=result;expires=android.os.SystemClock.elapsedRealtime()+FRESHNESS_MS;}
                        return result;
                    }catch(Exception unavailable){synchronized(LOCK){expires=android.os.SystemClock.elapsedRealtime()+15000;}throw unavailable;
                    }finally{synchronized(LOCK){loading=null;}}
                });
                REFRESH.execute(loading);
            }
            task=loading;
            // A warm feed is served immediately. Network work never holds its lock.
            if(snapshot!=null)return page(snapshot,start,limit);
        }
        JSONArray initial;
        try{initial=task.get();}catch(ExecutionException failed){Throwable cause=failed.getCause();if(cause instanceof Exception)throw (Exception)cause;throw failed;}
        synchronized(LOCK){if(snapshot==null){snapshot=initial;if(refreshed==initial)refreshed=null;}return page(snapshot,start,limit);}
    }
    static JSONArray select(JSONArray records,java.util.Set<Integer> used,int limit)throws Exception {
        JSONArray selected=new JSONArray();
        for(int i=0;i<records.length()&&selected.length()<limit;i++){
            JSONObject record=records.getJSONObject(i);int id=record.optInt("anilistId");
            if(id>0&&!record.optBoolean("adult")&&used.add(id))selected.put(record);
        }
        return selected;
    }
    static boolean acceptRefresh(JSONArray current,JSONArray replacement){return replacement!=null&&replacement.length()>0&&(current==null||replacement.length()*2>=current.length());}
    static JSONObject continueRow()throws Exception {return new JSONObject().put("id","ani-continue-watching").put("resource_type","CONTINUE_WATCHING").put("response_type","HISTORY").put("title","Continue Watching").put("link","/content/v2/discover/me/history");}
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
            for(String[] c:COLLECTIONS){final String id=c[0];tasks.add(pool.submit(()->records(id)));}
            JSONArray feed=new JSONArray();
            List<JSONArray> collections=new ArrayList<>();JSONArray artwork=new JSONArray();
            LinkedHashSet<Integer> used=new LinkedHashSet<>();
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);
            for(int i=0;i<COLLECTIONS.length;i++){
                JSONArray records;
                try{records=tasks.get(i).get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);}catch(ExecutionException|TimeoutException e){records=new JSONArray();}
                JSONArray selected=select(records,used,ROW_SIZE);
                collections.add(selected);for(int j=0;j<selected.length();j++)artwork.put(selected.get(j));
            }
            try{NativeMetadata.artwork(artwork);}catch(Exception optional){android.util.Log.w("APKForgeMetadata","Home artwork refresh unavailable; keeping catalog artwork");}
            for(int i=0;i<COLLECTIONS.length;i++){
                JSONArray records=collections.get(i);
                BackendBridge.cache(records);String label=COLLECTIONS[i][1];if(records.length()>0&&records.getJSONObject(0).optString("_homeSource").equals("anilist")){if(COLLECTIONS[i][0].equals("popular")||COLLECTIONS[i][0].equals("monthly"))label="Popular";if(COLLECTIONS[i][0].equals("trending"))label="Trending";}JSONObject row=collection(COLLECTIONS[i][0],label,records);
                if(row.getJSONArray("ids").length()==0)continue;
                if(feed.length()==0){
                    String heroId=row.getJSONArray("ids").getString(0);int key=Integer.parseInt(heroId.substring(3));
                    for(int n=0;n<records.length();n++)if(records.getJSONObject(n).optInt("anilistId")==key){
                        JSONObject hero=records.getJSONObject(n);
                        feed.put(new JSONObject().put("id","ani-hero").put("resource_type","PANEL").put("response_type","UNDEFINED")
                            .put("display_type","HERO").put("panel",BackendBridge.panel(hero)).put("title",hero.optString("title")));break;
                    }
                }
                feed.put(row);
            }
            if(feed.length()==0)throw new BackendBridge.HttpFailure(503,"catalog-unavailable");
            JSONArray withProgress=new JSONArray();for(int i=0;i<feed.length();i++){withProgress.put(feed.get(i));if(i==0)withProgress.put(continueRow());}return withProgress;
        }finally{pool.shutdownNow();}
    }
    private static JSONArray records(String id)throws Exception {
        if(id.equals("airing")||id.equals("season"))return NativeDiscovery.feed(id);
        try{return BackendBridge.api(query(id).replace("limit=100","limit=32")).getJSONArray("data");}
        catch(Exception unavailable){android.util.Log.w("APKForgeMetadata","Home collection "+id+" uses metadata fallback: "+unavailable.getClass().getSimpleName());return NativeDiscovery.feed(id);}
    }
}
