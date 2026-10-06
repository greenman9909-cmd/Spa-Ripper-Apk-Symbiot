package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

/** Replacement community API; guest reads and authenticated owner writes. */
final class NativeCommunity {
    static JSONObject route(Uri uri,String method,JSONObject body,LocalProfiles profile)throws Exception {
        java.util.List<String> segments=uri.getPathSegments();
        boolean signedIn=!CloudSession.userId().isEmpty();
        if(segments.size()==2&&method.equals("GET")){
            JSONArray items=new JSONArray();String keys=uri.getQueryParameter("guestbook_keys");
            if(keys!=null)for(String key:keys.split(",")){validateBook(key);items.put(new JSONObject(CloudSession.rest("/rest/v1/rpc/comment_book","POST",new JSONObject().put("book",key),signedIn)));}
            return new JSONObject().put("items",items);
        }
        if(segments.size()<3)throw new BackendBridge.HttpFailure(404,"comment-not-found");
        String book=segments.get(2);validateBook(book);
        if(segments.size()==3&&method.equals("GET"))return new JSONObject(CloudSession.rest("/rest/v1/rpc/comment_book","POST",new JSONObject().put("book",book),signedIn));
        if(segments.size()<4||!segments.get(3).equals("comments"))throw new BackendBridge.HttpFailure(404,"comment-not-found");
        String id=segments.size()>4?segments.get(4):null;if(id!=null)id=databaseId(id,signedIn);
        if(method.equals("GET")){
            JSONObject request=new JSONObject().put("book",book).put("page_number",Math.max(1,NativeDiscovery.integer(uri.getQueryParameter("page"),1)))
                .put("page_size",Math.max(1,Math.min(100,NativeDiscovery.integer(uri.getQueryParameter("page_size"),50))))
                .put("ascending_order","asc".equalsIgnoreCase(uri.getQueryParameter("order"))||"ascending".equalsIgnoreCase(uri.getQueryParameter("order")));
            if(id!=null)request.put(segments.size()>5&&segments.get(5).equals("replies")?"parent":"comment_uuid",id);
            JSONObject result=normalizeDates(new JSONObject(CloudSession.rest("/rest/v1/rpc/comment_feed","POST",request,signedIn)));
            return id!=null&&segments.size()==5?first(result.getJSONArray("items")):result;
        }
        if(!signedIn)throw new BackendBridge.HttpFailure(401,"cloud-login-required");
        if(id!=null&&segments.size()==6&&segments.get(5).equals("votes")){
            String type=method.equals("DELETE")?uri.getQueryParameter("vote_type"):body.optString("vote_type","");
            if(type==null||!type.toLowerCase(java.util.Locale.US).matches("like|spoiler|inappropriate"))throw new BackendBridge.HttpFailure(400,"invalid-vote");
            type=type.toLowerCase(java.util.Locale.US);
            if(method.equals("POST"))CloudSession.rest("/rest/v1/app_comment_votes?on_conflict=comment_id,user_id,vote_type","POST",new JSONObject().put("comment_id",id).put("vote_type",type),true);
            else if(method.equals("DELETE"))CloudSession.rest("/rest/v1/app_comment_votes?comment_id=eq."+id+"&vote_type=eq."+type,"DELETE",null,true);
            else throw new BackendBridge.HttpFailure(405,"invalid-vote-method");
            return new JSONObject();
        }
        if(method.equals("POST")&&id==null){
            String message=body.optString("message","").trim();if(message.isEmpty()||message.length()>2000)throw new BackendBridge.HttpFailure(400,"invalid-comment");
            JSONObject p=profile.route("/accounts/v1/me/multiprofile/"+profile.selectedId(),"GET",new JSONObject());
            JSONObject row=new JSONObject().put("guestbook_key",book).put("message",message).put("author_name",p.optString("profile_name","Guest"))
                .put("avatar_id",p.optString("avatar","default.png")).put("spoiler",hasSpoiler(body));
            String parent=NativeMetadata.string(body,"parent_id");if(!parent.isEmpty())row.put("parent_id",databaseId(parent,true));
            JSONArray created=new JSONArray(CloudSession.rest("/rest/v1/app_comments","POST",row,true));return readOne(book,first(created).getString("id"));
        }
        if(id!=null&&method.equals("DELETE")&&segments.size()==5){
            CloudSession.rest("/rest/v1/app_comments?id=eq."+id+"&guestbook_key=eq."+book,"DELETE",null,true);return new JSONObject();
        }
        if(id!=null&&method.equals("PATCH")&&segments.size()==6&&segments.get(5).equals("flags")){
            JSONArray changed=new JSONArray(CloudSession.rest("/rest/v1/app_comments?id=eq."+id+"&guestbook_key=eq."+book,"PATCH",new JSONObject().put("spoiler",hasSpoiler(new JSONObject().put("flags",body.optJSONArray("add")))),true));
            first(changed);return readOne(book,id);
        }
        throw new BackendBridge.HttpFailure(501,"community-action-not-configured");
    }
    static void validateBook(String value)throws Exception {if(!value.matches("ANI[0-9]+(?:E[0-9]+D?)?"))throw new BackendBridge.HttpFailure(400,"invalid-guestbook");}
    private static String databaseId(String value,boolean authenticated)throws Exception {
        if(!value.matches("[1-9][0-9]{0,17}"))throw new BackendBridge.HttpFailure(400,"invalid-comment-id");
        return first(new JSONArray(CloudSession.rest("/rest/v1/app_comments?native_id=eq."+value+"&select=id","GET",null,authenticated))).getString("id");
    }
    private static boolean hasSpoiler(JSONObject body){JSONArray flags=body.optJSONArray("flags");if(flags!=null)for(int i=0;i<flags.length();i++)if("SPOILER".equalsIgnoreCase(flags.optString(i)))return true;return false;}
    private static JSONObject first(JSONArray items)throws Exception {if(items.length()==0)throw new BackendBridge.HttpFailure(404,"comment-not-found");return items.getJSONObject(0);}
    private static JSONObject readOne(String book,String id)throws Exception {return first(normalizeDates(new JSONObject(CloudSession.rest("/rest/v1/rpc/comment_feed","POST",new JSONObject().put("book",book).put("comment_uuid",id),true))).getJSONArray("items"));}
    // The retained Talkbox client has its own date adapter and expects +0000 offsets.
    static JSONObject normalizeDates(JSONObject response)throws Exception {
        JSONArray items=response.optJSONArray("items");if(items==null)return response;
        java.time.format.DateTimeFormatter format=java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ",java.util.Locale.US).withZone(java.time.ZoneOffset.UTC);
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);
            for(String field:new String[]{"created","modified"})if(!item.isNull(field)){
                try{item.put(field,format.format(java.time.OffsetDateTime.parse(item.getString(field)).toInstant()));}
                catch(java.time.DateTimeException invalid){throw new java.io.IOException("Invalid comment timestamp",invalid);}
            }
        }return response;
    }
    // Pure empty-model fixtures used by contract tests. Live requests use the overload above.
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
