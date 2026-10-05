package dev.apkforge.bridge;

import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import javax.net.ssl.HttpsURLConnection;

/** Adapts public catalog data to the existing native model boundary. No View code. */
public final class BackendBridge {
    private static final Map<Integer,JSONObject> titles = new java.util.LinkedHashMap<Integer,JSONObject>(64,0.75f,true){
        protected boolean removeEldestEntry(Map.Entry<Integer,JSONObject> entry){return size()>500;}
    };
    private static final Map<String,CachedResponse> responses = new java.util.LinkedHashMap<String,CachedResponse>(32,0.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,CachedResponse> entry){return size()>64;}
    };
    private static final class CachedResponse {
        final String json;final long expires;
        CachedResponse(String json){this.json=json;expires=android.os.SystemClock.elapsedRealtime()+120000;}
    }
    private static final String TAG="APKForgeBridge";
    private static final String BASE="https://ani.pm/api/partner/v1";
    private static LocalProfiles profiles;
    static final class HttpFailure extends Exception {
        final int status;
        HttpFailure(int status,String error){super(error);this.status=status;}
    }
    private static synchronized LocalProfiles profiles(ClassLoader loader)throws Exception {
        CloudSession.init(loader);
        if(profiles==null||!profiles.cloudOwner.equals(CloudSession.userId()))profiles=new LocalProfiles(loader);return profiles;
    }
    private BackendBridge() {}
    static boolean replacementHost(String host){
        return host!=null&&(host.equals("crunchyroll.com")||host.endsWith(".crunchyroll.com")
            ||host.equals("cr-play-service.prd.crunchyrollsvc.com"));
    }
    public static void install(Object builder) {
        try {
            ClassLoader loader=builder.getClass().getClassLoader();Class<?> interceptor=Class.forName("me0.u",true,loader);
            Object proxy=Proxy.newProxyInstance(loader,new Class<?>[]{interceptor},(self,method,args)->{
                if(method.getName().equals("toString"))return "APKForgeCatalogAdapter";
                if(method.getName().equals("hashCode"))return System.identityHashCode(self);
                if(method.getName().equals("equals"))return self==args[0];
                if(!method.getName().equals("intercept"))throw new UnsupportedOperationException(method.getName());
                return intercept(loader,args[0]);
            });
            builder.getClass().getMethod("a",interceptor).invoke(builder,proxy);
        } catch(Exception e){throw new IllegalStateException("Original network interface changed",e);}
    }
    private static Object intercept(ClassLoader loader,Object chain) throws Exception {
        Class<?> chainType=Class.forName("me0.u$a",true,loader);
        Object request=chainType.getMethod("request").invoke(chain);
        String address=request.getClass().getField("a").get(request).toString(); Uri uri=Uri.parse(address);
        String host=uri.getHost(),path=uri.getPath();
        if(!replacementHost(host)){
            return chainType.getMethod("e",Class.forName("me0.a0",true,loader)).invoke(chain,request);
        }
        Log.i(TAG,"Catalog request "+path); // No query, headers, cookies or credentials.
        int status=200;String data;
        try{data=route(loader,request,uri).toString();}
        catch(HttpFailure e){status=e.status;data=new JSONObject().put("error",e.getMessage()).toString();}
        catch(UnsupportedOperationException e){status=501;data=new JSONObject().put("error","unmapped-native-route").toString();Log.w(TAG,"Unmapped native route "+path);}
        catch(Exception e){status=503;data=new JSONObject().put("error","provider-unavailable").toString();Log.w(TAG,"Provider request failed for "+path+": "+e.getClass().getSimpleName());}
        Class<?> responseBuilder=Class.forName("me0.e0$a",true,loader);Object response=responseBuilder.getConstructor().newInstance();
        responseBuilder.getField("a").set(response,request);
        @SuppressWarnings({"unchecked","rawtypes"}) Object protocol=Enum.valueOf((Class)Class.forName("me0.z",true,loader),"HTTP_1_1");
        responseBuilder.getField("b").set(response,protocol);responseBuilder.getField("c").setInt(response,status);responseBuilder.getField("d").set(response,status==200?"OK":"Adapter incomplete");
        Class<?> mediaType=Class.forName("me0.w",true,loader);
        Object body=Class.forName("me0.f0$b",true,loader).getMethod("b",String.class,mediaType).invoke(null,data,null);
        responseBuilder.getField("g").set(response,body);return responseBuilder.getMethod("a").invoke(response);
    }
    private static String requestBody(ClassLoader loader,Object request)throws Exception {
        Object body=request.getClass().getField("d").get(request);if(body==null)return "";
        Object buffer=Class.forName("af0.d",true,loader).getConstructor().newInstance();
        Class.forName("me0.d0",true,loader).getMethod("c",Class.forName("af0.f",true,loader)).invoke(body,buffer);
        return (String)buffer.getClass().getMethod("y").invoke(buffer);
    }
    private static JSONObject route(ClassLoader loader,Object request,Uri uri) throws Exception {
        String path=uri.getPath();
        if(path.contains("config_delta"))return new JSONObject().put("config_delta",new JSONObject());
        if(path.endsWith("index/v2"))return new JSONObject().put("service_available",true);
        // Legacy model label for replacement-backend capabilities only. This is
        // not an official subscription, token, license or access to its media.
        if(path.startsWith("/subs/")&&path.endsWith("/benefits"))return new JSONObject().put("items",new JSONArray().put(new JSONObject().put("benefit","cr_premium").put("source","apkforge-local")));
        // The replacement guest backend offers no subscriptions or purchases.
        if(path.startsWith("/subs/"))return new JSONObject().put("items",new JSONArray());
        if(path.startsWith("/skip-events/"))return new JSONObject().put("mediaId",path.substring(path.lastIndexOf('/')+1).replace(".json",""));
        if(path.startsWith("/talkbox/guestbooks"))return NativeCommunity.route(uri,(String)request.getClass().getField("b").get(request));
        if(NativeCatalog.handles(path))return NativeCatalog.route(uri);
        if(path.contains("/apkforge/playback/"))throw new HttpFailure(501,"native-playback-api-unavailable");
        if(path.matches("/v1/ANI[0-9]+E[0-9]+D?/android/phone/play"))throw new HttpFailure(501,"native-playback-api-unavailable");
        if(path.startsWith("/content-reviews/")){
            String method=(String)request.getClass().getField("b").get(request);
            String raw=requestBody(loader,request);JSONObject body=raw.isEmpty()?new JSONObject():new JSONObject(raw);
            String id=path.substring(path.lastIndexOf('/')+1);LocalProfiles p=profiles(loader);
            synchronized(p){
                JSONObject ratings=p.state("ratings");
                if(method.equals("DELETE")){ratings.remove(id);p.saveState("ratings",ratings);return new JSONObject();}
                if(method.equals("PUT")){ratings.put(id,body.optString("rating"));p.saveState("ratings",ratings);}
                return NativeRatings.container(path.contains("/rating/episode/"),ratings.optString(id));
            }
        }
        if(path.contains("/music/featured/"))return envelope(new JSONArray());
        if(path.contains("/similar_to/")){
            String id=path.substring(path.lastIndexOf('/')+1);JSONObject title=series(Integer.parseInt(id.substring(3)));
            JSONArray genres=title.optJSONArray("genres"),found=api("/top?range=all&adult=0&limit=100").getJSONArray("data"),similar=new JSONArray();cache(found);
            for(int i=0;i<found.length()&&similar.length()<20;i++){
                JSONObject candidate=found.getJSONObject(i);if(candidate.getInt("anilistId")==title.getInt("anilistId"))continue;
                JSONArray cg=candidate.optJSONArray("genres");boolean match=false;
                if(genres!=null&&cg!=null)for(int g=0;g<genres.length();g++)for(int c=0;c<cg.length();c++)if(genres.getString(g).equals(cg.getString(c)))match=true;
                if(match)similar.put(panel(candidate));
            }
            return envelope(similar);
        }
        if(path.endsWith("/token")){
            Uri form=Uri.parse("https://local/?"+requestBody(loader,request));
            String grant=form.getQueryParameter("grant_type");
            if("password".equals(grant)){
                CloudSession.init(loader);CloudSession.signIn(form.getQueryParameter("username"),form.getQueryParameter("password"));
            }else if(!"refresh_token".equals(grant)&&!"refresh_token_profile_id".equals(grant)&&!"client_id".equals(grant))throw new HttpFailure(501,"login-method-not-configured");
            LocalProfiles p=profiles(loader);String id=form.getQueryParameter("profile_id");if(id!=null)p.select(id);
            return new JSONObject().put("access_token","apkforge-local-guest").put("refresh_token","apkforge-local-guest")
                .put("token_type","Bearer").put("expires_in",3600).put("country","FR").put("scope","offline_access")
                .put("account_id",p.accountId()).put("profile_id",p.selectedId());
        }
        if(path.endsWith("/logout")||path.endsWith("/auth/v1/revoke")){CloudSession.init(loader);CloudSession.signOut();return new JSONObject();}
        if(path.endsWith("/accounts/v1/me"))return profiles(loader).account();
        if(path.contains("/custom-lists")){
            String method=(String)request.getClass().getField("b").get(request),raw=requestBody(loader,request);LocalProfiles p=profiles(loader);
            synchronized(p){JSONObject state=p.state("custom-lists");JSONObject result=NativeLists.route(state,uri,method,raw.isEmpty()?new JSONObject():new JSONObject(raw));
                if(!method.equals("GET"))p.saveState("custom-lists",state);return result;}
        }
        if(path.contains("/watch-history")){
            String method=(String)request.getClass().getField("b").get(request);
            if(method.equals("GET"))return envelope(new JSONArray()); // Native playback has not produced history yet.
            throw new HttpFailure(501,"history-writes-not-configured");
        }
        if(path.contains("/accounts/v1/me/multiprofile")||path.endsWith("/accounts/v1/usernames")){
            String method=(String)request.getClass().getField("b").get(request);
            String body=requestBody(loader,request);
            return profiles(loader).route(path,method,body.isEmpty()?new JSONObject():new JSONObject(body));
        }
        if(path.contains("/watchlist")||path.contains("/playheads")){
            String method=(String)request.getClass().getField("b").get(request);
            String body=requestBody(loader,request);
            return profileContent(profiles(loader),uri,method,body.isEmpty()?new JSONObject():new JSONObject(body));
        }
        if(path.endsWith("/home_feed")){NativeAccountState.initialize(loader);return NativeHomeFeed.home();}
        if(path.endsWith("/search")){
            String q=uri.getQueryParameter("q");if(q==null||q.trim().length()<2)return envelope(new JSONArray());
            JSONArray found=api("/titles?adult=0&limit=25&q="+URLEncoder.encode(q.trim(),"UTF-8")).getJSONArray("data");cache(found);
            JSONArray converted=new JSONArray();for(int i=0;i<found.length();i++)converted.put(panel(found.getJSONObject(i)));
            return envelope(new JSONArray().put(new JSONObject().put("type","SERIES").put("count",converted.length()).put("items",converted))).put("total",converted.length());
        }
        if(path.endsWith("/browse")||path.contains("/discover/apkforge-")){
            String query=path.endsWith("/browse")?"/top?range=all&adult=0&limit=100":NativeHomeFeed.query(path.substring(path.lastIndexOf("apkforge-")+9));
            JSONArray found=api(query).getJSONArray("data");cache(found);
            JSONArray converted=new JSONArray();int start=Math.max(0,integer(uri.getQueryParameter("start"),0)),limit=integer(uri.getQueryParameter("n"),25);
            for(int i=start;i<Math.min(found.length(),start+Math.max(1,Math.min(limit,100)));i++)converted.put(panel(found.getJSONObject(i)));
            return envelope(converted).put("total",found.length());
        }
        if(path.contains("/objects/")){
            String ids=path.substring(path.lastIndexOf('/')+1);JSONArray converted=new JSONArray();
            for(String id:ids.split(",")){
                if(id.matches("ANI[0-9]+E[0-9]+D?")){JSONObject episode=NativeCatalog.route(Uri.parse("https://local/content/v2/cms/episodes/"+id)).getJSONArray("data").getJSONObject(0);converted.put(NativeCatalog.episodePanel(episode));}
                else if(id.matches("ANI[0-9]+")){int n=Integer.parseInt(id.substring(3));JSONObject title; synchronized(titles){title=titles.get(n);}if(title==null)title=series(n);converted.put(panel(title));}
            }
            return envelope(converted);
        }
        if(path.contains("/history")||path.contains("/categories")||path.contains("/seasonal_tags"))return envelope(new JSONArray());
        throw new UnsupportedOperationException(path);
    }
    private static JSONObject profileContent(LocalProfiles profile,Uri uri,String method,JSONObject body)throws Exception {
        String path=uri.getPath(),category=path.contains("/watchlist")?"watchlist":"playheads";
        synchronized(profile){
            JSONObject saved=profile.state(category);
            if(!method.equals("GET")){
                String id=body.optString("content_id",path.substring(path.lastIndexOf('/')+1));
                if(!id.matches("ANI[0-9]+(?:E[0-9]+D?)?"))throw new HttpFailure(400,"invalid-content-id");
                if(method.equals("DELETE"))saved.remove(id);
                else if(category.equals("watchlist"))saved.put(id,new JSONObject().put("id",id).put("is_favorite",body.optBoolean("is_favorite")));
                else saved.put(id,new JSONObject().put("content_id",id).put("playhead",Math.max(0,body.optLong("playhead")))
                    .put("fully_watched",false).put("last_modified",new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",java.util.Locale.US){{setTimeZone(java.util.TimeZone.getTimeZone("UTC"));}}.format(new java.util.Date())));
                profile.saveState(category,saved);return new JSONObject();
            }
            JSONArray data=new JSONArray();java.util.Iterator<String> ids=saved.keys();
            while(ids.hasNext()){
                String id=ids.next();JSONObject item=saved.getJSONObject(id);
                if(path.contains("/discover/")&&category.equals("watchlist")){
                    JSONObject title=series(Integer.parseInt(id.substring(3)));data.put(new JSONObject().put("panel",NativeCatalog.watchlistPanel(title))
                        .put("is_favorite",item.optBoolean("is_favorite")).put("playhead",0).put("never_watched",true).put("fully_watched",false));
                }else if(category.equals("watchlist")||uri.getQueryParameter("content_ids")==null||java.util.Arrays.asList(uri.getQueryParameter("content_ids").split(",")).contains(id))data.put(item);
            }
            return envelope(data);
        }
    }
    private static int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}
    static void cache(JSONArray data)throws Exception{synchronized(titles){for(int i=0;i<data.length();i++){
        JSONObject t=data.getJSONObject(i);int id=t.getInt("anilistId");JSONObject existing=titles.get(id);
        // Summary refreshes must not discard the already loaded episode list.
        if(existing==null||!existing.has("episodeList")||t.has("episodeList"))titles.put(id,t);
    }}}
    static JSONObject envelope(JSONArray data)throws Exception{return new JSONObject().put("data",data).put("total",data.length()).put("meta",new JSONObject());}
    static JSONObject panel(JSONObject title)throws Exception{
        JSONObject ep=title.optJSONObject("episodes");if(ep==null)ep=new JSONObject();
        JSONObject tall=new JSONObject().put("source",title.optString("poster")).put("height",900).put("width",600).put("type","poster_tall");
        boolean hasBanner=!title.isNull("banner")&&!title.optString("banner").isEmpty();
        // poster_wide is the original native screen's cropped presentation.
        // Supplying portrait dimensions here expands its hero to a full poster.
        JSONObject wide=new JSONObject().put("source",hasBanner?title.optString("banner"):title.optString("poster")).put("height",360).put("width",640).put("type","poster_wide");
        JSONObject images=new JSONObject().put("poster_tall",new JSONArray().put(new JSONArray().put(tall))).put("poster_wide",new JSONArray().put(new JSONArray().put(wide)));
        JSONObject metadata=new JSONObject().put("season_count",1).put("episode_count",ep.optInt("total")).put("is_subbed",ep.optInt("sub")>0).put("is_dubbed",ep.optInt("dub")>0).put("maturity_ratings",new JSONArray().put("TV-14")).put("audio_locales",new JSONArray().put("ja-JP").put("en-US")).put("subtitle_locales",new JSONArray().put("en-US"));
        return new JSONObject().put("id","ANI"+title.getInt("anilistId")).put("type","series").put("title",title.optString("title")).put("description",title.optString("synopsis")).put("images",images).put("series_metadata",metadata).put("channel_id","crunchyroll");
    }
    static JSONObject series(int id)throws Exception {
        JSONObject title;synchronized(titles){title=titles.get(id);}
        if(title!=null&&title.has("episodeList"))return title;
        title=api("/series/"+id+"?adult=0").getJSONObject("data");synchronized(titles){titles.put(id,title);}return title;
    }
    static JSONObject api(String path)throws Exception{
        synchronized(responses){CachedResponse hit=responses.get(path);if(hit!=null&&hit.expires>android.os.SystemClock.elapsedRealtime())return new JSONObject(hit.json);}
        HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","APKForge/0.4 (Android)");c.setRequestProperty("Accept","application/json");
        try{if(c.getResponseCode()!=200)throw new java.io.IOException("Provider HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>8*1024*1024)throw new java.io.IOException("Catalog response too large");bytes.write(buffer,0,n);}String json=new String(bytes.toByteArray(),StandardCharsets.UTF_8);JSONObject result=new JSONObject(json);synchronized(responses){responses.put(path,new CachedResponse(json));}return result;}}finally{c.disconnect();}
    }
}
