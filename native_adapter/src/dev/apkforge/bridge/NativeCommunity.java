package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

/** The replacement backend has no community comments yet. Reads are empty. */
final class NativeCommunity {
    static JSONObject route(Uri uri,String method)throws Exception {
        if(!method.equals("GET"))throw new BackendBridge.HttpFailure(501,"community-writes-not-configured");
        String path=uri.getPath();
        if(path.equals("/talkbox/guestbooks")){
            JSONArray books=new JSONArray();String keys=uri.getQueryParameter("guestbook_keys");
            if(keys!=null)for(String key:keys.split(","))books.put(book(key));
            return new JSONObject().put("items",books);
        }
        if(path.matches("/talkbox/guestbooks/[^/]+"))return book(path.substring(path.lastIndexOf('/')+1));
        if(path.matches("/talkbox/guestbooks/[^/]+/comments(?:/[^/]+/replies)?"))
            return new JSONObject().put("items",new JSONArray()).put("total",0);
        throw new BackendBridge.HttpFailure(404,"comment-not-found");
    }
    private static JSONObject book(String key)throws Exception {
        return new JSONObject().put("guestbook_key",key).put("total_comments",0);
    }
}
