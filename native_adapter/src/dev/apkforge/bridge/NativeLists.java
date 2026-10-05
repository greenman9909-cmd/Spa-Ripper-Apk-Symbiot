package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Private custom lists use the same per-profile local store as the watchlist. */
final class NativeLists {
    static final int MAX_LISTS=10,MAX_ITEMS=100;
    private static String now(){
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));return f.format(new java.util.Date());
    }
    static JSONObject route(JSONObject saved,Uri uri,String method,JSONObject body)throws Exception {
        List<String> path=uri.getPathSegments();int index=path.indexOf("custom-lists");
        if(index<0)throw new BackendBridge.HttpFailure(404,"list-not-found");
        if(path.size()==index+1){
            if(method.equals("POST")){
                if(saved.length()>=MAX_LISTS)throw new BackendBridge.HttpFailure(409,"list-limit-reached");
                String id=java.util.UUID.randomUUID().toString();JSONObject list=new JSONObject().put("list_id",id)
                    .put("title",title(body)).put("modified_at",now()).put("is_public",false).put("entries",new JSONArray());
                saved.put(id,list);return BackendBridge.envelope(new JSONArray().put(summary(list)));
            }
            if(!method.equals("GET"))throw new BackendBridge.HttpFailure(405,"method-not-allowed");
            JSONArray lists=new JSONArray();java.util.Iterator<String> keys=saved.keys();
            while(keys.hasNext())lists.put(summary(saved.getJSONObject(keys.next())));
            return BackendBridge.envelope(lists).put("meta",new JSONObject().put("max_private",MAX_LISTS).put("total_private",lists.length()).put("total_public",0));
        }
        String id=path.get(index+1);JSONObject list=saved.optJSONObject(id);
        if(list==null)throw new BackendBridge.HttpFailure(404,"list-not-found");
        JSONArray entries=list.getJSONArray("entries");
        if(path.size()==index+2){
            if(method.equals("DELETE")){saved.remove(id);return new JSONObject();}
            if(method.equals("PATCH")){list.put("title",title(body)).put("modified_at",now());return new JSONObject();}
            if(method.equals("POST")){
                String content=body.optString("content_id");
                if(!content.matches("ANI[0-9]+"))throw new BackendBridge.HttpFailure(400,"invalid-series-id");
                for(int i=0;i<entries.length();i++)if(entries.getString(i).equals(content))return new JSONObject();
                if(entries.length()>=MAX_ITEMS)throw new BackendBridge.HttpFailure(409,"list-item-limit-reached");
                BackendBridge.series(Integer.parseInt(content.substring(3))); // Verify existence before persisting.
                entries.put(content);list.put("modified_at",now());return new JSONObject();
            }
            if(method.equals("GET"))return items(list,uri);
        }
        if(path.size()==index+3&&method.equals("DELETE")){
            for(int i=0;i<entries.length();i++)if(entries.getString(i).equals(path.get(index+2))){entries.remove(i);list.put("modified_at",now());break;}
            return new JSONObject();
        }
        if(path.size()==index+4&&path.get(index+3).equals("position")&&method.equals("PUT")){
            String moving=path.get(index+2),ref=body.optString("ref_content_id"),location=body.optString("location");
            ArrayList<String> ids=new ArrayList<>();for(int i=0;i<entries.length();i++)ids.add(entries.getString(i));
            if(!ids.contains(moving)||!ids.contains(ref)||moving.equals(ref)||(!location.equals("BEFORE")&&!location.equals("AFTER")))throw new BackendBridge.HttpFailure(400,"invalid-list-position");
            ids.remove(moving);ids.add(ids.indexOf(ref)+(location.equals("AFTER")?1:0),moving);
            list.put("entries",new JSONArray(ids)).put("modified_at",now());return new JSONObject();
        }
        throw new BackendBridge.HttpFailure(405,"method-not-allowed");
    }
    private static String title(JSONObject body)throws Exception {
        String title=body.optString("title").trim();if(title.isEmpty()||title.length()>100)throw new BackendBridge.HttpFailure(400,"invalid-list-title");return title;
    }
    private static JSONObject summary(JSONObject list)throws Exception {
        return new JSONObject().put("list_id",list.getString("list_id")).put("title",list.getString("title"))
            .put("modified_at",list.getString("modified_at")).put("is_public",false).put("total",list.getJSONArray("entries").length());
    }
    private static JSONObject items(JSONObject list,Uri uri)throws Exception {
        JSONArray entries=list.getJSONArray("entries");ArrayList<JSONObject> all=new ArrayList<>();
        for(int i=0;i<entries.length();i++){
            String id=entries.getString(i);all.add(new JSONObject().put("id",id).put("list_id",list.getString("list_id"))
                .put("modified_at",list.getString("modified_at")).put("panel",BackendBridge.panel(BackendBridge.series(Integer.parseInt(id.substring(3))))));
        }
        String sort=uri.getQueryParameter("sort_by"),order=uri.getQueryParameter("order");
        if("title".equalsIgnoreCase(sort))Collections.sort(all,Comparator.comparing(item->item.optJSONObject("panel").optString("title"),String.CASE_INSENSITIVE_ORDER));
        if("desc".equalsIgnoreCase(order)||"descending".equalsIgnoreCase(order))Collections.reverse(all);
        int page=positive(uri.getQueryParameter("page"),1),size=Math.min(MAX_ITEMS,positive(uri.getQueryParameter("page_size"),20));
        long start=(long)(page-1)*size;JSONArray data=new JSONArray();for(long i=start;i<Math.min(all.size(),start+size);i++)data.put(all.get((int)i));
        JSONObject meta=new JSONObject().put("max",MAX_ITEMS).put("is_public",false).put("title",list.getString("title")).put("modified_at",list.getString("modified_at"))
            .put("next_page",start+size<all.size()?String.valueOf(page+1):JSONObject.NULL);
        return BackendBridge.envelope(data).put("total",all.size()).put("meta",meta);
    }
    private static int positive(String value,int fallback){try{return Math.max(1,Integer.parseInt(value));}catch(Exception e){return fallback;}}
}
