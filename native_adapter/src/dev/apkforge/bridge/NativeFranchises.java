package dev.apkforge.bridge;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Only explicit same-format TV prequel/sequel edges become seasons. No title guessing. */
final class NativeFranchises {
    private static final Object[] DETAIL_LOCKS=new Object[16];static{for(int i=0;i<DETAIL_LOCKS.length;i++)DETAIL_LOCKS[i]=new Object();}
    private static final java.util.concurrent.ThreadPoolExecutor SEASONS=new java.util.concurrent.ThreadPoolExecutor(3,3,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<Runnable>(16),task->{Thread thread=new Thread(task,"APKForgeSeasonDetails");thread.setDaemon(true);return thread;});
    interface Fetch {JSONObject get(int id)throws Exception;}
    static final String FIELDS="id idMal format startDate{year month day} title{english romaji native} description(asHtml:false) coverImage{extraLarge large} bannerImage episodes genres status isAdult relations{edges{relationType node{id format status isAdult}}}";
    private static final Map<Integer,JSONObject> RECORDS=new LinkedHashMap<Integer,JSONObject>(128,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Integer,JSONObject> e){return size()>512;}
    };
    private static final Map<Integer,Entry> DETAILS=new LinkedHashMap<Integer,Entry>(8,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Integer,NativeFranchises.Entry> e){return size()>8;}
    };
    private static final class Entry {final JSONObject value;final long expires;Entry(JSONObject v){value=v;expires=android.os.SystemClock.elapsedRealtime()+(v.optBoolean("_availabilityUnknown")||v.optBoolean("_detailsIncomplete")?15000:120000);}}
    static void remember(JSONArray records){synchronized(RECORDS){for(int i=0;i<records.length();i++){JSONObject r=records.optJSONObject(i);if(r!=null&&r.optInt("anilistId")>0)RECORDS.put(r.optInt("anilistId"),r);}}}
    private static boolean tv(JSONObject r){return r!=null&&("TV".equals(r.optString("format"))||"TV_SHORT".equals(r.optString("format")))&&!r.optBoolean("adult")&&!"NOT_YET_RELEASED".equals(r.optString("status"));}
    static List<Integer> links(JSONObject record,String direction){
        List<Integer> ids=new ArrayList<>();if(!tv(record))return ids;
        JSONArray edges=record.optJSONArray("relations");if(edges==null)return ids;
        for(int i=0;i<edges.length();i++){JSONObject e=edges.optJSONObject(i);if(e==null||!direction.equals(e.optString("relationType")))continue;JSONObject n=e.optJSONObject("node");
            if(n!=null&&record.optString("format").equals(n.optString("format"))&&!n.optBoolean("isAdult")&&!"NOT_YET_RELEASED".equals(n.optString("status"))&&n.optInt("id")>0)ids.add(n.optInt("id"));}
        return ids;
    }
    static List<Integer> chain(int id){synchronized(RECORDS){
        Set<Integer> seen=new HashSet<>();int root=id;
        while(seen.add(root)&&seen.size()<16){List<Integer> previous=links(RECORDS.get(root),"PREQUEL");if(previous.size()!=1||!RECORDS.containsKey(previous.get(0)))break;root=previous.get(0);}
        List<Integer> out=new ArrayList<>();seen.clear();int current=root;
        while(seen.add(current)&&out.size()<16){out.add(current);List<Integer> next=links(RECORDS.get(current),"SEQUEL");if(next.size()!=1||!RECORDS.containsKey(next.get(0)))break;current=next.get(0);}
        // Ambiguous branches or cycles do not justify discarding the requested entry.
        return out.contains(id)?out:Collections.singletonList(id);
    }}
    static JSONArray collapse(JSONArray records)throws Exception {
        remember(records);JSONArray out=new JSONArray();Set<Integer> seen=new HashSet<>();
        for(int i=0;i<records.length();i++){JSONObject record=records.getJSONObject(i);List<Integer> ids=chain(record.getInt("anilistId"));int root=ids.get(0);if(!seen.add(root))continue;
            JSONObject first,last;synchronized(RECORDS){first=RECORDS.get(root);last=RECORDS.get(ids.get(ids.size()-1));}
            if(first==null)first=record;JSONObject grouped=copy(first);JSONArray seasons=new JSONArray();int total=0;
            synchronized(RECORDS){for(int j=0;j<ids.size();j++){seasons.put(j+1);JSONObject r=RECORDS.get(ids.get(j));if(r!=null&&r.optJSONObject("episodes")!=null)total+=r.optJSONObject("episodes").optInt("total");}}
            if(ids.size()>1){grouped.put("_seasonNumbers",seasons).put("episodes",new JSONObject().put("total",total));if(last!=null)for(String k:new String[]{"poster","banner"})if(!NativeMetadata.string(last,k).isEmpty())grouped.put(k,last.get(k));}
            out.put(grouped);
        }BackendBridge.cache(out);return out;
    }
    static JSONObject series(int id)throws Exception {
        synchronized(DETAIL_LOCKS[(id&Integer.MAX_VALUE)%DETAIL_LOCKS.length]){return details(id);}
    }
    private static JSONObject details(int id)throws Exception {
        JSONObject known=BackendBridge.cachedTitle(id);if(known!=null&&known.has("episodeList")&&!known.has("_detailsLoadedAt"))return known;
        Entry hit;synchronized(DETAILS){hit=DETAILS.get(id);}if(hit!=null&&hit.expires>android.os.SystemClock.elapsedRealtime())return hit.value;
        // Batch a bounded frontier; metadata failure keeps the individual working title.
        Set<Integer> visited=new HashSet<>();List<Integer> pending=new ArrayList<>();pending.add(id);
        try{for(int pass=0;pass<8&&!pending.isEmpty()&&visited.size()<16;pass++){
            JSONArray queryIds=new JSONArray();List<Integer> frontier=new ArrayList<>(pending);pending.clear();
            for(int n:frontier)if(visited.add(n)&&visited.size()<=16){JSONObject r;synchronized(RECORDS){r=RECORDS.get(n);}if(r!=null&&r.has("relations")&&android.os.SystemClock.elapsedRealtime()-r.optLong("_relationshipsLoadedAt",-120000)<120000){for(String d:new String[]{"PREQUEL","SEQUEL"})for(int linked:links(r,d))if(!visited.contains(linked))pending.add(linked);}else queryIds.put(n);}if(queryIds.length()==0)continue;
            JSONArray media=NativeMetadata.graph("query($ids:[Int]){Page(perPage:16){media(id_in:$ids,type:ANIME,isAdult:false){"+FIELDS+"}}}",new JSONObject().put("ids",queryIds)).getJSONObject("Page").getJSONArray("media");
            JSONArray records=new JSONArray();for(int i=0;i<media.length();i++){JSONObject r=NativeDiscovery.record(media.getJSONObject(i));records.put(r);for(String d:new String[]{"PREQUEL","SEQUEL"})for(int n:links(r,d))if(!visited.contains(n))pending.add(n);}remember(records);
        }}catch(Exception unavailable){android.util.Log.w("APKForgeMetadata","Season relationships unavailable; retaining individual title");}
        List<Integer> ids=chain(id);JSONObject requested=BackendBridge.series(id);
        List<JSONObject> titles=relatedSources(ids,id,requested,BackendBridge::series,18000);
        if(titles.size()<2){JSONObject single=copy(requested);if(ids.size()>1)single.put("_detailsIncomplete",true);synchronized(DETAILS){DETAILS.put(id,new Entry(single));}return single;}
        JSONObject merged=merge(titles);if(titles.size()<ids.size())merged.put("_detailsIncomplete",true);Entry entry=new Entry(merged);synchronized(DETAILS){for(JSONObject loaded:titles)DETAILS.put(loaded.getInt("anilistId"),entry);}return merged;
    }
    static List<JSONObject> relatedSources(List<Integer> ids,int requested,JSONObject current,Fetch fetch,long budgetMillis)throws Exception {
        if(current==null||current.optInt("anilistId")!=requested||current.optJSONArray("episodeList")==null||current.optJSONArray("episodeList").length()==0)return Collections.emptyList();
        Map<Integer,java.util.concurrent.Future<JSONObject>> tasks=new LinkedHashMap<>();
        try{for(int n:ids)if(n!=requested)try{tasks.put(n,SEASONS.submit(()->fetch.get(n)));}catch(java.util.concurrent.RejectedExecutionException busy){/* Keep the requested title usable when season workers are full. */}
            long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(Math.max(1,budgetMillis));List<JSONObject> out=new ArrayList<>();
            for(int n:ids){JSONObject record=current;if(n!=requested){java.util.concurrent.Future<JSONObject> task=tasks.get(n);if(task==null)continue;try{record=task.get(Math.max(1,deadline-System.nanoTime()),java.util.concurrent.TimeUnit.NANOSECONDS);}catch(java.util.concurrent.ExecutionException|java.util.concurrent.TimeoutException unavailable){continue;}}
                JSONArray episodes=record==null?null:record.optJSONArray("episodeList");if(record!=null&&record.optInt("anilistId")==n&&episodes!=null&&episodes.length()>0)out.add(record);
            }return out;
        }finally{for(java.util.concurrent.Future<JSONObject> task:tasks.values())if(!task.isDone())task.cancel(true);SEASONS.purge();}
    }
    static JSONObject artwork(int id){synchronized(DETAILS){Entry entry=DETAILS.get(id);return entry==null?null:entry.value;}}
    static JSONObject merge(List<JSONObject> sources)throws Exception {
        JSONObject result=copy(sources.get(0));JSONArray episodes=new JSONArray(),numbers=new JSONArray();int group=0,sub=0,dub=0;
        for(JSONObject source:sources){JSONArray eps=source.getJSONArray("episodeList");Map<Integer,Integer> groups=new LinkedHashMap<>();
            for(int i=0;i<eps.length();i++){JSONObject original=eps.getJSONObject(i);int old=NativeSeasons.number(original);if(!groups.containsKey(old)){groups.put(old,++group);numbers.put(group);}JSONObject e=copy(original);
                e.put("seasonNumber",groups.get(old)).put("_sourceId",source.getInt("anilistId"));episodes.put(e);JSONObject a=e.optJSONObject("available");if(a!=null){if(a.optBoolean("sub"))sub++;if(a.optBoolean("dub"))dub++;}}
        }
        JSONObject latest=sources.get(sources.size()-1);for(String k:new String[]{"poster","banner"})if(!NativeMetadata.string(latest,k).isEmpty())result.put(k,latest.get(k));
        return result.put("episodeList",episodes).put("_seasonNumbers",numbers).put("episodes",new JSONObject().put("total",episodes.length()).put("sub",sub).put("dub",dub));
    }
    private static JSONObject copy(JSONObject value)throws Exception {JSONObject out=new JSONObject();Iterator<String> keys=value.keys();while(keys.hasNext()){String k=keys.next();out.put(k,value.get(k));}return out;}
}
