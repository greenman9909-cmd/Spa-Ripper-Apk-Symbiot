package dev.apkforge.bridge;

import org.json.JSONObject;
import java.lang.reflect.Method;

/** Executes against the actual retained model classes, not reimplemented models. */
public final class ModelContractProbe {
    private static Object gson;
    private static Method deserialize;
    private static Object decode(String name,JSONObject json)throws Exception {
        return deserialize.invoke(gson,json.toString(),Class.forName(name));
    }
    private static Object call(Object value,String method)throws Exception {return value.getClass().getMethod(method).invoke(value);}
    private static Object decodeComment(JSONObject data)throws Exception {
        java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ",java.util.Locale.US);
        Object adapter=Class.forName("com.ellation.crunchyroll.api.DateTypeAdapter").getConstructor(java.text.SimpleDateFormat.class,java.text.SimpleDateFormat.class,java.text.SimpleDateFormat.class).newInstance(format,format,new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",java.util.Locale.US));
        Object builder=Class.forName("com.google.gson.GsonBuilder").getConstructor().newInstance();
        builder.getClass().getMethod("registerTypeAdapter",java.lang.reflect.Type.class,Object.class).invoke(builder,java.util.Date.class,adapter);
        Object talkbox=builder.getClass().getMethod("create").invoke(builder);
        return talkbox.getClass().getMethod("fromJson",String.class,Class.class).invoke(talkbox,data.toString(),Class.forName("com.ellation.crunchyroll.api.etp.commenting.model.Comment"));
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        gson=Class.forName("com.ellation.crunchyroll.api.GsonHolder").getMethod("getInstance").invoke(null);
        deserialize=gson.getClass().getMethod("fromJson",String.class,Class.class);
        int checks=0;
        for(String rating:new String[]{"","UP","DOWN","NONE"}){
            Object model=decode("com.ellation.crunchyroll.api.etp.contentreviews.model.episode.EpisodeRatingContainer",NativeRatings.container(true,rating));
            Object up=call(model,"getUp"),down=call(model,"getDown");
            check(up!=null&&down!=null,"Episode rating stats must exist for the retained player");
            check(((Number)call(up,"getRawRatingCount")).intValue()==(rating.equals("UP")?1:0),"Wrong up count");
            check(((Number)call(down,"getRawRatingCount")).intValue()==(rating.equals("DOWN")?1:0),"Wrong down count");
            check(call(model,"getUserContentRating").toString().equals(rating.equals("UP")||rating.equals("DOWN")?rating:"NONE"),"Wrong episode rating enum");checks++;
        }
        for(String rating:new String[]{"","1s","5s"}){
            Object model=decode("com.ellation.crunchyroll.api.etp.contentreviews.model.ContentRatingContainer",NativeRatings.container(false,rating));
            for(String method:new String[]{"getOneStar","getTwoStars","getThreeStars","getFourStars","getFiveStars"})check(call(model,method)!=null,"Missing series stats");
            check(((Number)call(model,"getAverage")).intValue()==(rating.isEmpty()?0:Integer.parseInt(rating.substring(0,1))),"Wrong series average");checks++;
        }
        JSONObject title=new JSONObject().put("anilistId",154587).put("title","Contract fixture").put("poster","https://example.invalid/poster.jpg")
            .put("episodes",new JSONObject().put("total",28).put("sub",28).put("dub",28));
        Object panel=decode("com.ellation.crunchyroll.model.Panel",BackendBridge.panel(title));
        check(call(panel,"getResourceType").toString().equals("series"),"Panel must be navigable by original screen");
        Object images=call(panel,"getImages");java.util.List<?> wide=(java.util.List<?>)call(images,"getPostersWide");
        Object image=wide.get(0);check(((Number)call(image,"getWidth")).intValue()==640&&((Number)call(image,"getHeight")).intValue()==360,"Wide hero presentation ratio");checks++;
        Object book=decode("com.ellation.crunchyroll.api.etp.commenting.model.Guestbook",NativeCommunity.route(android.net.Uri.parse("https://local/talkbox/guestbooks/ANI269E1"),"GET"));
        check(call(book,"getGuestbookKey").equals("ANI269E1")&&((Number)call(book,"getTotalComments")).intValue()==0,"Empty replacement guestbook contract");checks++;
        Object comments=decode("com.ellation.crunchyroll.api.etp.commenting.model.CommentPreview",NativeCommunity.route(android.net.Uri.parse("https://local/talkbox/guestbooks/ANI269E1/comments"),"GET"));
        check(((java.util.List<?>)call(comments,"getComments")).isEmpty()&&((Number)call(comments,"getTotal")).intValue()==0,"Empty replacement comment preview contract");checks++;
        try{NativeCommunity.route(android.net.Uri.parse("https://local/talkbox/guestbooks/ANI269E1/comments"),"POST");throw new AssertionError("Must not pretend to save comments");}
        catch(BackendBridge.HttpFailure expected){check(expected.status==501,"Explicit unsupported community write");checks++;}
        JSONObject lists=new JSONObject();android.net.Uri listsUri=android.net.Uri.parse("https://local/content/v2/local/custom-lists");
        JSONObject created=NativeLists.route(lists,listsUri,"POST",new JSONObject().put("title","Native contract list"));
        JSONObject record=created.getJSONArray("data").getJSONObject(0);String listId=record.getString("list_id");
        Object createdModel=decode("com.ellation.crunchyroll.api.etp.content.model.customlists.CreatedCustomList",record);
        check(call(createdModel,"getListId").equals(listId)&&call(createdModel,"getModifiedAt")!=null,"Created list date/id contract");checks++;
        Object listsModel=decode("com.ellation.crunchyroll.api.etp.content.model.customlists.CustomLists",NativeLists.route(lists,listsUri,"GET",new JSONObject()));
        check(((java.util.List<?>)call(listsModel,"getItems")).size()==1&&((Number)call(call(listsModel,"getMetadata"),"getMaxPrivate")).intValue()==10,"List collection metadata contract");checks++;
        android.net.Uri listUri=android.net.Uri.parse(listsUri+"/"+listId);
        NativeLists.route(lists,listUri,"PATCH",new JSONObject().put("title","Renamed"));
        Object itemsModel=decode("com.ellation.crunchyroll.api.etp.content.model.customlists.CustomListItems",NativeLists.route(lists,listUri,"GET",new JSONObject()));
        check(((java.util.List<?>)call(itemsModel,"getItems")).isEmpty()&&call(call(itemsModel,"getMetadata"),"getTitle").equals("Renamed"),"Empty list detail and rename contract");checks++;
        try{NativeLists.route(lists,listsUri,"POST",new JSONObject().put("title"," "));throw new AssertionError("Invalid title must fail");}
        catch(BackendBridge.HttpFailure expected){check(expected.status==400&&lists.length()==1,"Invalid title must not mutate lists");checks++;}
        NativeLists.route(lists,listUri,"DELETE",new JSONObject());check(lists.length()==0,"Delete list contract");checks++;
        JSONObject providerEpisode=new JSONObject().put("number",1).put("runtimeSeconds",1431).put("available",new JSONObject().put("sub",true).put("dub",true));
        Object episodeModel=decode("com.ellation.crunchyroll.model.Episode",NativeCatalog.episode(title,providerEpisode));
        check(call(episodeModel,"getOriginalAudio").equals("ja-JP")&&((java.util.List<?>)call(episodeModel,"getVersions")).size()==2,"Selectable audio versions prevent locale reload loop");checks++;
        title.put("episodeList",new org.json.JSONArray().put(providerEpisode).put(new JSONObject(providerEpisode.toString()).put("number",2)));
        java.lang.reflect.Field cache=BackendBridge.class.getDeclaredField("titles");cache.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Map<Integer,JSONObject> cached=(java.util.Map<Integer,JSONObject>)cache.get(null);cached.put(154587,title);
        JSONObject next=NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/discover/up_next/ANI154587E1"));
        check(next.getJSONArray("data").getJSONObject(0).getJSONObject("panel").getString("id").equals("ANI154587E2"),"Next episode must not point back to itself");checks++;
        check(NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/discover/up_next/ANI154587E2")).getJSONArray("data").length()==0,"End of series must have no next episode");checks++;
        Object dubbed=decode("com.ellation.crunchyroll.model.Episode",NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/cms/episodes/ANI154587E1D")).getJSONArray("data").getJSONObject(0));
        check(call(dubbed,"getAudioLocale").equals("en-US")&&call(dubbed,"getId").equals("ANI154587E1D"),"Dubbed asset locale/id must agree");checks++;
        Object watchlistPanel=decode("com.ellation.crunchyroll.model.Panel",NativeCatalog.watchlistPanel(title));
        check(call(watchlistPanel,"getResourceType").toString().equals("episode")&&call(call(watchlistPanel,"getPanelMetadata"),"getParentId").equals("ANI154587"),"Watchlist row must expose episode image and series parent");checks++;
        check(BackendBridge.replacementHost("cr-play-service.prd.crunchyrollsvc.com"),"Native player uses a separate original service hostname");checks++;
        check(!BackendBridge.replacementHost("crunchyroll.com.example.invalid")&&!BackendBridge.replacementHost(null),"Replacement must not intercept unrelated domains");checks++;
        org.json.JSONArray records=new org.json.JSONArray().put(title).put(title).put(new JSONObject(title.toString()).put("anilistId",99).put("adult",true));
        Object feed=decode("com.ellation.crunchyroll.api.model.HomeFeedItemRaw",NativeHomeFeed.collection("popular","Popular This Week",records));
        check((Boolean)call(feed,"isValid")&&call(feed,"getResourceType").toString().equals("CURATED_COLLECTION")&&call(feed,"getResponseType").toString().equals("SERIES"),"Original Home collection model");
        check(((java.util.List<?>)call(feed,"getItemsIds")).size()==1&&call(feed,"getLink").equals("/content/v2/discover/apkforge-popular"),"Home must deduplicate records and exclude adult titles");checks++;
        check(NativeHomeFeed.query("popular").contains("range=week")&&NativeHomeFeed.query("movies").contains("format=MOVIE"),"View All must use the same collection filters");checks++;
        try{NativeHomeFeed.query("unknown");throw new AssertionError("Unknown collection must fail");}catch(BackendBridge.HttpFailure expected){check(expected.status==404,"Unknown collection status");checks++;}
        JSONObject seasonalTags=NativeHomeFeed.seasonalTags();
        JSONObject seasonal=seasonalTags.getJSONArray("data").getJSONObject(0);
        check(seasonal.getString("id").equals("current")&&seasonal.getJSONObject("localization").getString("title").equals("Currently Airing"),"Simulcast season tag must expose a resolvable live tag");checks++;
        check(NativeHomeFeed.seasonalQuery("current").contains("range=today"),"Simulcast tag must use the live provider collection");checks++;
        org.json.JSONArray feedRows=new org.json.JSONArray().put(new JSONObject().put("id","hero")).put(new JSONObject().put("id","row-1")).put(new JSONObject().put("id","row-2"));
        JSONObject firstPage=NativeHomeFeed.page(feedRows,0,2),secondPage=NativeHomeFeed.page(feedRows,2,2);
        check(firstPage.getJSONArray("data").length()==2&&secondPage.getJSONArray("data").length()==1&&secondPage.getJSONArray("data").getJSONObject(0).getString("id").equals("row-2"),"Scrolling must not repeat the hero or earlier rows");checks++;
        check(firstPage.getInt("total")==3&&secondPage.getInt("total")==3&&NativeHomeFeed.page(feedRows,3,2).getJSONArray("data").length()==0,"Home total and empty final page must terminate pagination");checks++;
        check(NativeHomeFeed.page(feedRows,Integer.MAX_VALUE,Integer.MAX_VALUE).getJSONArray("data").length()==0&&NativeHomeFeed.page(feedRows,-1,0).getJSONArray("data").length()==1,"Home pagination bounds must not overflow or fail");checks++;
        JSONObject summary=new JSONObject(title.toString());summary.remove("episodeList");
        BackendBridge.cache(new org.json.JSONArray().put(summary));
        check(BackendBridge.series(154587).has("episodeList"),"Home summary refresh must retain loaded episodes");checks++;
        check(call(watchlistPanel,"getStreamHref").equals("/apkforge/playback/ANI154587E1"),"A playable native Panel must retain its stream link");checks++;
        Object onlyDub=decode("com.ellation.crunchyroll.model.Episode",NativeCatalog.episode(title,new JSONObject(providerEpisode.toString()).put("available",new JSONObject().put("sub",false).put("dub",true))));
        check(call(onlyDub,"getAudioLocale").equals("en-US")&&call(onlyDub,"getId").equals("ANI154587E1D"),"Dub-only episodes must not advertise an unavailable Japanese asset");checks++;
        Class<?> premiumFlag=Class.forName("id0.a");
        Object guestFlag=java.lang.reflect.Proxy.newProxyInstance(premiumFlag.getClassLoader(),new Class<?>[]{premiumFlag},(self,method,arguments)->Boolean.FALSE);
        Object availability=Class.forName("ov.c").getConstructor(premiumFlag).newInstance(guestFlag);
        check(availability.getClass().getMethod("a",Class.forName("com.ellation.crunchyroll.model.PlayableAsset")).invoke(availability,episodeModel).equals("available"),"Provider-available episode must pass original date-based availability contract");checks++;
        JSONObject hlsJson=NativePlayback.streamJson("ANI21E1","https://example.invalid/master.m3u8","sub",
            new org.json.JSONArray().put(new JSONObject().put("format","vtt").put("language","en").put("file","https://example.invalid/en.vtt")));
        Object hls=decode("com.ellation.crunchyroll.api.cms.model.streams.Streams",hlsJson);
        Object nativePlayer=NativePlayback.mapHls("ANI21E1",hls,null,"");
        check(nativePlayer!=null&&nativePlayer.getClass().getField("d").get(nativePlayer).toString().equals("HLS"),"Original player must select HLS rather than DASH");
        check(nativePlayer.getClass().getField("e").get(nativePlayer).equals("https://example.invalid/master.m3u8")&&((java.util.List<?>)call(nativePlayer,"f")).size()==1,"Original URL and external subtitle mapping");
        check(nativePlayer.getClass().getField("g").get(nativePlayer)==null&&nativePlayer.getClass().getField("j").get(nativePlayer)==null,"Public HLS must not synthesize DRM tokens or licensed sessions");checks++;
        JSONObject emptyHls=new JSONObject(hlsJson.toString()).put("streams",new JSONObject());
        check(NativePlayback.mapHls("ANI21E1",decode("com.ellation.crunchyroll.api.cms.model.streams.Streams",emptyHls),null,"")==null,"Non-HLS streams retain original mapper");checks++;
        check(NativePlayback.decodeSources("wdeBruh3qqn_i5wUNnyaPYD8arCx-VSkn9ax1dWdZbr5zTx8zxHC6TDwg54JqrA1").getString("file").equals("https://example.invalid/master.m3u8"),"Public provider AES-256 zero-padded-key compatibility fixture");checks++;
        for(String unsafe:new String[]{"http://example.invalid/a","https://127.0.0.1/a","https://user:password@example.invalid/a","file:///a"}){
            try{NativePlayback.publicHttps(unsafe);throw new AssertionError("Invalid source URL accepted");}catch(java.io.IOException expected){}
        }checks++;
        Object category=decode("com.ellation.crunchyroll.model.categories.Category",NativeDiscovery.categories().getJSONArray("data").getJSONObject(0));
        check(call(category,"getTenantCategoryId").equals("action")&&call(call(category,"getLocalization"),"getTitle").equals("Action"),"Native category localization contract");checks++;
        JSONObject filters=NativeDiscovery.variables(android.net.Uri.parse("https://local/browse?categories=sci-fi,action&seasonal_tag=2026-fall&sort_by=alphabetical&q=Test"));
        check(filters.getJSONArray("genres").getString(0).equals("Sci-Fi")&&filters.getString("season").equals("FALL")&&filters.getInt("year")==2026&&filters.getJSONArray("sort").getString(0).equals("TITLE_ROMAJI"),"Discovery filters must preserve genre/year/sort semantics");checks++;
        check(NativeDiscovery.lowerBound(2,50,true)==101&&NativeDiscovery.lowerBound(3,7,false)==107,"Paging must use hasNextPage instead of inaccurate provider totals");checks++;
        try{NativeDiscovery.variables(android.net.Uri.parse("https://local/browse?categories=unknown"));throw new AssertionError("Unknown genres must fail");}catch(BackendBridge.HttpFailure expected){check(expected.status==400,"Invalid category status");checks++;}
        JSONObject enriched=new JSONObject(title.toString());enriched.getJSONArray("episodeList").getJSONObject(0).put("thumbnail",JSONObject.NULL).put("title",JSONObject.NULL);
        NativeMetadata.mergeEpisodes(enriched,new JSONObject().put("1",new JSONObject().put("image","https://example.invalid/episode1.jpg").put("title",new JSONObject().put("en","A real episode title")).put("overview","Episode overview")));
        JSONObject enrichedEpisode=NativeCatalog.episode(enriched,enriched.getJSONArray("episodeList").getJSONObject(0));
        check(enrichedEpisode.getString("title").equals("A real episode title")&&enrichedEpisode.getJSONObject("images").getJSONArray("thumbnail").getJSONArray(0).getJSONObject(0).getString("source").endsWith("episode1.jpg")&&enrichedEpisode.getJSONArray("versions").length()==2,"Episode metadata enrichment must retain source audio availability");checks++;
        JSONObject skip=NativePlayback.mapSkipEvents("ANI21E1",new JSONObject().put("intro",new JSONObject().put("start",31).put("end",111)).put("outro",new JSONObject().put("start",1376).put("end",1447)));
        Object skips=decode("com.ellation.crunchyroll.api.etp.playback.model.SkipEvents",skip);
        check(((Number)call(call(skips,"getIntro"),"getEndSeconds")).doubleValue()==111&&call(call(skips,"getCredits"),"getType").toString().equals("CREDITS"),"Native skip events must use provider timestamps and original enums");checks++;
        check(!NativePlayback.mapSkipEvents("ANI21E1",new JSONObject().put("intro",new JSONObject().put("start",10).put("end",10))).has("intro"),"Empty or invalid skip ranges must not produce controls");checks++;
        check(NativeAssets.avatar("ani-cover-21").endsWith("anilistId=21")&&NativeAssets.avatar("default.png").startsWith("https://ani.pm/")&&NativeAssets.avatar("https://user:password@example.invalid")==null,"Native asset URL mapping must only accept own catalog IDs");checks++;
        JSONObject nullable=new JSONObject().put("id",99).put("title",new JSONObject().put("english",JSONObject.NULL).put("romaji","Localized title")).put("description",JSONObject.NULL).put("coverImage",new JSONObject().put("large","https://example.invalid/cover.jpg"));
        check(NativeDiscovery.record(nullable).getString("title").equals("Localized title")&&NativeDiscovery.record(nullable).getString("synopsis").isEmpty(),"JSON null metadata must never render the literal null");checks++;
        JSONObject commentResponse=NativeCommunity.normalizeDates(new JSONObject().put("items",new org.json.JSONArray().put(new JSONObject().put("comment_id","123456").put("created","2026-10-05T12:26:58.718602+00:00").put("modified","2026-10-05T14:26:58.718602+02:00"))));
        Object datedComment=decodeComment(commentResponse.getJSONArray("items").getJSONObject(0));
        check(call(datedComment,"getCreated").equals(call(datedComment,"getModified")),"Cloud comment dates must parse in the original Gson adapter without a crash");checks++;
        check(Long.parseLong(call(datedComment,"getId").toString())==123456,"Original comment RecyclerView requires numeric stable IDs, not database UUIDs");checks++;
        check(NativeSubtitles.locale(new JSONObject().put("language","es").put("label","Spanish")).equals("es-ES"),"Spanish native locale mapping");checks++;
        check(NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Spanish (- Spanish(Latin America))")).equals("es-419"),"Latin American Spanish must not collapse into Spain");checks++;
        check(NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Portuguese (- Portuguese(Brazil))")).equals("pt-BR")&&NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Russian")).equals("ru-RU")&&NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Unknown")).isEmpty(),"Label fallback must preserve supported languages and reject unknown tracks");checks++;
        String ass=NativeSubtitles.toAss("WEBVTT\n\nintro\n00:01.250 --> 00:04.590 align:center\nHola, <b>mundo</b>!\nSecond line\n\n01:01:02.000 --> 01:01:03.000\nEspañol\n");
        check(ass.contains("Dialogue: 0,0:00:01.25,0:00:04.59,Default,,0,0,0,,Hola, mundo!\\NSecond line")&&ass.contains("1:01:02.00,1:01:03.00"),"WebVTT conversion must preserve cue timing, Unicode and line breaks");checks++;
        try{NativeSubtitles.toAss("<html>upstream error</html>");throw new AssertionError("HTML must not be accepted as subtitles");}catch(java.io.IOException expected){}checks++;
        check(!NativeSubtitles.toAss("WEBVTT\n\n00:01.000 --> 00:02.000\n{\\pos(1,2)}Text\n").contains("{\\pos"),"Subtitle data must not inject ASS overrides");checks++;
        check(NativePlayback.hardLocale(new JSONObject().put("hardsub_locale","es-419")).equals("es-419")&&NativePlayback.hardLocale(new JSONObject().put("type","sub")).isEmpty(),"Sub audio alone is not proof of burned-in subtitles");checks++;
        JSONObject spanishStreams=NativePlayback.streamJson("ANI154587E1","https://example.invalid/master.m3u8","sub",new org.json.JSONArray().put(new JSONObject().put("format","vtt").put("language","es").put("file","https://example.invalid/es.vtt")).put(new JSONObject().put("format","vtt").put("language","und").put("label","Spanish (Latin America)").put("file","https://example.invalid/latam.vtt")));
        Object spanishPlayer=NativePlayback.mapHls("ANI154587E1",decode("com.ellation.crunchyroll.api.cms.model.streams.Streams",spanishStreams),null,"");
        check(((java.util.List<?>)call(spanishPlayer,"f")).size()==2&&spanishStreams.getJSONObject("subtitles").getJSONObject("es-419").getString("url").startsWith("https://appassets.androidplatform.net/apkforge-subtitles/"),"Separate Spanish tracks must reach the original player through its local ASS renderer");checks++;
        System.out.println("Native model contracts passed: "+checks);
        for(String arg:args)if(arg.equals("--catalog-live")){
            JSONObject one=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?start=0&n=25")),two=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?start=25&n=25"));
            java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<one.getJSONArray("data").length();i++)ids.add(one.getJSONArray("data").getJSONObject(i).getString("id"));
            for(int i=0;i<two.getJSONArray("data").length();i++)check(ids.add(two.getJSONArray("data").getJSONObject(i).getString("id")),"Catalog pages repeat title IDs");
            check(one.getJSONArray("data").length()==25&&two.getJSONArray("data").length()==25,"Catalog page lengths");
            cached.remove(154587);JSONObject detail=BackendBridge.series(154587);NativePlayback.publicHttps(NativeMetadata.string(detail.getJSONArray("episodeList").getJSONObject(0),"thumbnail"));
            JSONObject ranges=NativePlayback.skipEvents("ANI21E1");check(ranges.has("intro"),"Live intro metadata");
            System.out.println("Live hybrid catalog passed: disjoint pages, episode image, intro metadata");
        }
        for(String arg:args)if(arg.equals("--stream-live")){
            JSONObject live=NativePlayback.resolve("ANI21E1");
            check(NativePlayback.mapHls("ANI21E1",decode("com.ellation.crunchyroll.api.cms.model.streams.Streams",live),null,"")!=null,"Live provider must map into original player");
            System.out.println("Live HLS resolver passed: One Piece episode 1 sub (manifest and model only)");
        }
        for(String arg:args)if(arg.equals("--subtitles-live")){
            JSONObject bundle=new JSONObject(NativePlayback.get("https://anivexaapi-aniko2.hf.space/api/watch/154587/sub/1",null)).getJSONObject("ssub");
            org.json.JSONArray tracks=bundle.getJSONArray("subtitles");java.util.HashSet<String> verified=new java.util.HashSet<>();
            for(int i=0;i<tracks.length();i++){JSONObject track=tracks.getJSONObject(i);String language=NativeSubtitles.locale(track);if(language.equals("es-ES")||language.equals("es-419")||language.equals("en-US")){String converted=NativeSubtitles.toAss(NativePlayback.get(track.getString("file"),NativePlayback.REFERER));check(converted.contains("Dialogue: 0,"),"Subtitle conversion must produce timed cues");verified.add(language);}}
            check(verified.contains("es-ES")&&verified.contains("es-419")&&verified.contains("en-US"),"Live Spanish variants and English tracks required for this fixture");
            System.out.println("Live subtitles passed: Frieren episode 1 English, Spanish and Latin American Spanish fetched and converted to ASS");
        }
        if(args.length>0&&args[0].equals("--cloud-negative")){
            try{CloudSession.signIn("native-contract@example.invalid","invalid-contract-password");throw new AssertionError("Unknown cloud user must not authenticate");}
            catch(BackendBridge.HttpFailure expected){check(expected.status==400||expected.status==401,"Auth must reject invalid credentials");}
            check(CloudSession.userId().isEmpty(),"Rejected login must not create a session");
            Method request=CloudSession.class.getDeclaredMethod("request",String.class,String.class,JSONObject.class,String.class);request.setAccessible(true);
            try{request.invoke(null,"/rest/v1/account_state?select=*","GET",null,null);throw new AssertionError("Public client key must not read private profiles");}
            catch(java.lang.reflect.InvocationTargetException expected){check(expected.getCause() instanceof BackendBridge.HttpFailure,"Expected explicit private-data rejection");int status=((BackendBridge.HttpFailure)expected.getCause()).status;check(status==401||status==403,"Private snapshots must reject unauthenticated access");}
            System.out.println("Live Supabase rejection checks passed: 2");
        }
    }
}
