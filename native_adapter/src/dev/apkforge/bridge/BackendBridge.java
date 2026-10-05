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
    /** Reads encrypted session state only; never performs network I/O on startup. */
    public static String refreshMarker(Object storage){
        try{CloudSession.init(storage.getClass().getClassLoader());return CloudSession.userId().isEmpty()?null:"apkforge-cloud-session";}
        catch(Exception unavailable){return null;}
    }
    public static boolean signedIn(Object storage){return refreshMarker(storage)!=null;}
    static JSONObject cachedTitle(int id){synchronized(titles){return titles.get(id);}}
    static synchronized String preferredSubtitleLanguage(){
        try{return profiles==null?"en-US":profiles.route("/accounts/v1/me/multiprofile/"+profiles.selectedId(),"GET",new JSONObject()).optString("preferred_content_subtitle_language","en-US");}
        catch(Exception unavailable){return "en-US";}
    }
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
        if(path.startsWith("/content/v2/")||path.startsWith("/talkbox/")||path.contains("/accounts/v1/me")){
            CloudSession.init(loader);CloudSession.requireSession();
        }
        if(path.contains("config_delta"))return new JSONObject().put("config_delta",new JSONObject());
        if(path.endsWith("index/v2"))return new JSONObject().put("service_available",true);
        if(path.matches("/assets/v2/[^/]+/(avatar|wallpaper)"))return NativeAssets.catalog();
        if(path.endsWith("/accounts/v2")){
            CloudSession.init(loader);CloudSession.signUp(new JSONObject(requestBody(loader,request)));return new JSONObject();
        }
        if(path.endsWith("/accounts/v1/password_forgot")){
            CloudSession.init(loader);CloudSession.recover(new JSONObject(requestBody(loader,request)).optString("email"));return new JSONObject();
        }
        // Legacy model label for replacement-backend capabilities only. This is
        // not an official subscription, token, license or access to its media.
        if(path.startsWith("/subs/")&&path.endsWith("/benefits"))return new JSONObject().put("items",new JSONArray().put(new JSONObject().put("benefit","cr_premium").put("source","apkforge-local")));
        // The replacement guest backend offers no subscriptions or purchases.
        if(path.startsWith("/subs/"))return new JSONObject().put("items",new JSONArray());
        if(path.startsWith("/skip-events/"))return NativePlayback.skipEvents(path.substring(path.lastIndexOf('/')+1).replace(".json",""));
        if(path.startsWith("/talkbox/guestbooks")){
            String raw=requestBody(loader,request);return NativeCommunity.route(uri,(String)request.getClass().getField("b").get(request),raw.isEmpty()?new JSONObject():new JSONObject(raw),profiles(loader));
        }
        if(NativeCatalog.handles(path))return NativeCatalog.route(uri);
        if(path.contains("/apkforge/playback/"))return NativePlayback.resolve(path.substring(path.lastIndexOf('/')+1));
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
            CloudSession.init(loader);
            if(!"client_id".equals(grant))CloudSession.requireSession();
            LocalProfiles p=profiles(loader);String id=form.getQueryParameter("profile_id");if(id!=null)p.select(id);
            return new JSONObject().put("access_token","apkforge-cloud-session").put("refresh_token","apkforge-cloud-session")
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
        if(path.endsWith("/home_feed")){NativeAccountState.initialize(loader);return NativeHomeFeed.home(integer(uri.getQueryParameter("start"),0),integer(uri.getQueryParameter("n"),25));}
        if(path.endsWith("/discover/apkforge-airing")||path.endsWith("/discover/apkforge-season"))return NativeDiscovery.route(Uri.parse("https://local/browse?seasonal_tag="+(path.endsWith("airing")?"current":NativeDiscovery.currentSeasonId())+"&start="+Math.max(0,integer(uri.getQueryParameter("start"),0))+"&n="+integer(uri.getQueryParameter("n"),25)));
        if(NativeDiscovery.handles(path)){
            try{return NativeDiscovery.route(uri);}
            catch(java.io.IOException unavailable){Log.w(TAG,"Discovery metadata unavailable; trying bounded ani.pm fallback");}
        }
        if(path.endsWith("/search")){
            String q=uri.getQueryParameter("q");if(q==null||q.trim().length()<2)return envelope(new JSONArray());
            String term=q.trim().toLowerCase(java.util.Locale.US);
            JSONArray found=api("/titles?q="+URLEncoder.encode(q.trim(),"UTF-8")+"&adult=0&limit=25").getJSONArray("data");
            // Some provider edges return an empty title list while /top remains
            // available. Filter that real catalog as a bounded, deterministic fallback.
            if(found.length()==0){
                JSONArray candidates=api("/top?range=all&adult=0&limit=100").getJSONArray("data");found=new JSONArray();
                for(int i=0;i<candidates.length()&&found.length()<25;i++){
                    JSONObject candidate=candidates.getJSONObject(i);
                    if(candidate.optString("title").toLowerCase(java.util.Locale.US).contains(term)
                        ||candidate.optString("nativeTitle").toLowerCase(java.util.Locale.US).contains(term))found.put(candidate);
                }
            }
            cache(found);
            JSONArray converted=new JSONArray();for(int i=0;i<found.length();i++)converted.put(panel(found.getJSONObject(i)));
            return envelope(new JSONArray().put(new JSONObject().put("type","series").put("count",converted.length()).put("items",converted))).put("total",converted.length());
        }
        if(path.endsWith("/seasonal_tags"))return NativeHomeFeed.seasonalTags();
        if(path.endsWith("/browse")||path.contains("/discover/apkforge-")){
            String seasonal=uri.getQueryParameter("seasonal_tag");
            String query=seasonal!=null?NativeHomeFeed.seasonalQuery(seasonal):path.endsWith("/browse")?"/top?range=all&adult=0&limit=100":NativeHomeFeed.query(path.substring(path.lastIndexOf("apkforge-")+9));
            JSONArray found=api(query).getJSONArray("data");cache(found);
            JSONArray converted=new JSONArray();int start=Math.max(0,integer(uri.getQueryParameter("start"),0)),limit=integer(uri.getQueryParameter("n"),25),seen=0;
            for(int i=0;i<found.length()&&converted.length()<Math.max(1,Math.min(limit,100));i++){
                JSONObject record=found.getJSONObject(i);
                if(seasonal!=null&&!record.optString("status").toLowerCase(java.util.Locale.US).contains("airing"))continue;
                if(seen++<start)continue;converted.put(panel(record));
            }
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
        if(path.contains("/history")||path.contains("/categories"))return envelope(new JSONArray());
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
        else{
            // Keep immutable episode arrays by reference instead of serializing
            // hundreds of episodes while holding the artwork/title-cache lock.
            JSONObject merged=new JSONObject();java.util.Iterator<String> keys=existing.keys();
            while(keys.hasNext()){String key=keys.next();merged.put(key,existing.get(key));}
            for(String key:new String[]{"poster","banner","title","nativeTitle","synopsis","genres","status"})if(!t.isNull(key))merged.put(key,t.get(key));
            titles.put(id,merged);
        }
    }}}
    static JSONObject envelope(JSONArray data)throws Exception{return new JSONObject().put("data",data).put("total",data.length()).put("meta",new JSONObject());}
    static JSONObject panel(JSONObject title)throws Exception{
        JSONObject ep=title.optJSONObject("episodes");if(ep==null)ep=new JSONObject();
        JSONObject tall=new JSONObject().put("source",NativeMetadata.string(title,"poster")).put("height",900).put("width",600).put("type","poster_tall");
        boolean hasBanner=!title.isNull("banner")&&!title.optString("banner").isEmpty();
        // poster_wide is the original native screen's cropped presentation.
        // Supplying portrait dimensions here expands its hero to a full poster.
        JSONObject wide=new JSONObject().put("source",hasBanner?title.optString("banner"):title.optString("poster")).put("height",360).put("width",640).put("type","poster_wide");
        JSONObject images=new JSONObject().put("poster_tall",new JSONArray().put(new JSONArray().put(tall))).put("poster_wide",new JSONArray().put(new JSONArray().put(wide)));
        JSONArray audio=new JSONArray();if(ep.optInt("sub")>0)audio.put("ja-JP");if(ep.optInt("dub")>0)audio.put("en-US");
        JSONArray maturity=new JSONArray();if(!title.optString("rating","").isEmpty())maturity.put(title.getString("rating"));
        JSONObject metadata=new JSONObject().put("season_count",NativeCatalog.seasonNumbers(title).size()).put("episode_count",ep.optInt("total")).put("is_subbed",ep.optInt("sub")>0).put("is_dubbed",ep.optInt("dub")>0).put("maturity_ratings",maturity).put("audio_locales",audio).put("subtitle_locales",new JSONArray());
        return new JSONObject().put("id","ANI"+title.getInt("anilistId")).put("type","series").put("title",title.optString("title")).put("description",title.optString("synopsis")).put("images",images).put("series_metadata",metadata).put("channel_id","crunchyroll");
    }
    static JSONObject series(int id)throws Exception {
        JSONObject title;synchronized(titles){title=titles.get(id);}
        if(title!=null&&title.has("episodeList")&&(!title.has("_detailsLoadedAt")||android.os.SystemClock.elapsedRealtime()-title.optLong("_detailsLoadedAt")<120000))return title;
        try{JSONObject fresh=api("/series/"+id+"?adult=0").getJSONObject("data");NativeSeasons.apply(fresh,null);NativeMetadata.enrich(fresh);fresh.put("_detailsLoadedAt",android.os.SystemClock.elapsedRealtime());synchronized(titles){titles.put(id,fresh);trimDetails();}return fresh;}
        catch(java.io.IOException unavailable){if(title!=null&&title.has("episodeList"))return title;throw unavailable;}
    }
    static void trimDetails()throws Exception {
        int count=0;for(JSONObject record:titles.values())if(record.has("episodeList"))count++;
        for(Map.Entry<Integer,JSONObject> entry:titles.entrySet()){
            if(count<=8)break;JSONObject record=entry.getValue();if(!record.has("episodeList"))continue;
            JSONObject summary=new JSONObject();java.util.Iterator<String> keys=record.keys();
            while(keys.hasNext()){String key=keys.next();if(!key.equals("episodeList")&&!key.equals("_detailsLoadedAt"))summary.put(key,record.get(key));}
            entry.setValue(summary);count--;
        }
    }
    static void trimResponses(){
        long chars=0;for(CachedResponse record:responses.values())chars+=record.json.length();
        java.util.Iterator<CachedResponse> oldest=responses.values().iterator();while(chars>4*1024*1024&&oldest.hasNext()){chars-=oldest.next().json.length();oldest.remove();}
    }
    static void invalidate(String path){synchronized(responses){responses.remove(path);}}
    static JSONObject api(String path)throws Exception{
        synchronized(responses){CachedResponse hit=responses.get(path);if(hit!=null&&hit.expires>android.os.SystemClock.elapsedRealtime())return new JSONObject(hit.json);}
        HttpsURLConnection c=(HttpsURLConnection)new URL(BASE+path).openConnection();c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setRequestProperty("User-Agent","APKForge/0.4 (Android)");c.setRequestProperty("Accept","application/json");
        try{if(c.getResponseCode()!=200)throw new java.io.IOException("Provider HTTP "+c.getResponseCode());try(InputStream in=c.getInputStream()){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>8*1024*1024)throw new java.io.IOException("Catalog response too large");bytes.write(buffer,0,n);}String json=new String(bytes.toByteArray(),StandardCharsets.UTF_8);JSONObject result=new JSONObject(json);synchronized(responses){responses.put(path,new CachedResponse(json));trimResponses();}return result;}}finally{c.disconnect();}
    }
}
