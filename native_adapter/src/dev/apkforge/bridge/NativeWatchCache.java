package dev.apkforge.bridge;

import org.json.JSONObject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Short-lived memory only: share watch/skip requests without persisting media tokens. */
final class NativeWatchCache {
    interface Fetch {JSONObject get()throws Exception;}
    interface Clock {long now();}
    private static final class Entry {
        final String json;final long until;
        Entry(String json,long until){this.json=json;this.until=until;}
    }
    private final Clock clock;
    private final Object[] locks=new Object[16];
    private final Map<String,Entry> values=new LinkedHashMap<String,Entry>(16,.75f,true);
    NativeWatchCache(Clock clock){this.clock=clock;for(int i=0;i<locks.length;i++)locks[i]=new Object();}
    JSONObject get(String asset,Fetch fetch)throws Exception {
        synchronized(locks[(asset.hashCode()&Integer.MAX_VALUE)%locks.length]){
            synchronized(values){Entry hit=values.get(asset);if(hit!=null&&hit.until>clock.now()){if(hit.json==null)throw new IOException("Episode provider temporarily unavailable");return new JSONObject(hit.json);}}
            try{JSONObject result=fetch.get();String json=result.toString();if(json.length()>1048576)throw new IOException("Watch response too large");put(asset,new Entry(json,clock.now()+45000));return new JSONObject(json);}
            catch(Exception unavailable){put(asset,new Entry(null,clock.now()+5000));throw unavailable;}
        }
    }
    private void put(String key,Entry entry){synchronized(values){values.put(key,entry);long chars=0;for(Entry e:values.values())if(e.json!=null)chars+=e.json.length();java.util.Iterator<Entry> oldest=values.values().iterator();while((values.size()>16||chars>2*1024*1024)&&oldest.hasNext()){Entry e=oldest.next();if(e.json!=null)chars-=e.json.length();oldest.remove();}}}
}
