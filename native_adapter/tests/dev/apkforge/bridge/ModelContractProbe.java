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
        Object image=wide.get(0);check(((Number)call(image,"getWidth")).intValue()==1280&&((Number)call(image,"getHeight")).intValue()==720,"Wide hero presentation ratio");checks++;
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
        java.util.Set<Integer> homeIds=new java.util.LinkedHashSet<>();
        org.json.JSONArray selected=NativeHomeFeed.select(records,homeIds,14);
        check(selected.length()==1&&homeIds.size()==1,"Home selection excludes repeated and adult cards");checks++;
        org.json.JSONArray other=NativeHomeFeed.select(new org.json.JSONArray().put(title).put(new JSONObject(title.toString()).put("anilistId",20)).put(new JSONObject(title.toString()).put("anilistId",21)),homeIds,1);
        check(other.length()==1&&other.getJSONObject(0).getInt("anilistId")==20&&!homeIds.contains(21),"Home rows exclude IDs from earlier rows without claiming unseen cards");checks++;
        java.lang.reflect.Field active=NativeHomeFeed.class.getDeclaredField("snapshot"),ready=NativeHomeFeed.class.getDeclaredField("refreshed"),expiry=NativeHomeFeed.class.getDeclaredField("expires");
        active.setAccessible(true);ready.setAccessible(true);expiry.setAccessible(true);
        active.set(null,feedRows);ready.set(null,new org.json.JSONArray().put(new JSONObject().put("id","new-hero")).put(new JSONObject().put("id","new-row-1")).put(new JSONObject().put("id","new-row-2")));expiry.setLong(null,Long.MAX_VALUE);
        check(NativeHomeFeed.home(2,2).getJSONArray("data").getJSONObject(0).getString("id").equals("row-2"),"Completed refresh must not replace an active scroll's later pages");checks++;
        check(NativeHomeFeed.home(0,2).getJSONArray("data").getJSONObject(0).getString("id").equals("new-hero")&&NativeHomeFeed.home(2,2).getJSONArray("data").getJSONObject(0).getString("id").equals("new-row-2"),"A new feed starts with the completed refresh and keeps coherent pagination");checks++;
        java.lang.reflect.Field pending=NativeHomeFeed.class.getDeclaredField("loading");pending.setAccessible(true);
        pending.set(null,new java.util.concurrent.FutureTask<org.json.JSONArray>(() -> {throw new AssertionError("Fixture refresh must not run");}));expiry.setLong(null,0);
        java.util.concurrent.ExecutorService caller=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{check(caller.submit(() -> NativeHomeFeed.home(0,2)).get(1,java.util.concurrent.TimeUnit.SECONDS).getJSONArray("data").getJSONObject(0).getString("id").equals("new-hero"),"A warm Home must return the current page while a network refresh is pending");checks++;}
        finally{caller.shutdownNow();pending.set(null,null);active.set(null,null);ready.set(null,null);expiry.setLong(null,0);}
        JSONObject summary=new JSONObject(title.toString());summary.remove("episodeList");
        BackendBridge.cache(new org.json.JSONArray().put(summary));
        check(BackendBridge.series(154587).has("episodeList"),"Home summary refresh must retain loaded episodes");checks++;
        check(BackendBridge.series(154587).getJSONArray("episodeList")==title.getJSONArray("episodeList"),"Summary updates must preserve the loaded episode array without cloning it under the image-cache lock");checks++;
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
        Object category=decode("com.ellation.crunchyroll.model.categories.Category",NativeDiscovery.categories(false).getJSONArray("data").getJSONObject(0));
        check(call(category,"getTenantCategoryId").equals("action")&&call(call(category,"getLocalization"),"getTitle").equals("Action"),"Native category localization contract");checks++;
        JSONObject filters=NativeDiscovery.variables(android.net.Uri.parse("https://local/browse?categories=sci-fi,action&seasonal_tag=2026-fall&sort_by=alphabetical&q=Test"));
        check(filters.getJSONArray("genres").getString(0).equals("Sci-Fi")&&filters.getString("season").equals("FALL")&&filters.getInt("year")==2026&&filters.getJSONArray("sort").getString(0).equals("TITLE_ROMAJI"),"Discovery filters must preserve genre/year/sort semantics");checks++;
        check(NativeDiscovery.lowerBound(2,50,true)==101&&NativeDiscovery.lowerBound(3,7,false)==107,"Paging must use hasNextPage instead of inaccurate provider totals");checks++;
        try{NativeDiscovery.variables(android.net.Uri.parse("https://local/browse?categories=unknown"));throw new AssertionError("Unknown genres must fail");}catch(BackendBridge.HttpFailure expected){check(expected.status==400,"Invalid category status");checks++;}
        JSONObject enriched=new JSONObject(title.toString());enriched.getJSONArray("episodeList").getJSONObject(0).put("thumbnail",JSONObject.NULL).put("title",JSONObject.NULL);
        NativeMetadata.mergeEpisodes(enriched,new JSONObject().put("1",new JSONObject().put("image","https://example.invalid/episode1.jpg").put("title",new JSONObject().put("en","A real episode title")).put("overview","Episode overview")));
        JSONObject enrichedEpisode=NativeCatalog.episode(enriched,enriched.getJSONArray("episodeList").getJSONObject(0));
        check(enrichedEpisode.getString("title").equals("A real episode title")&&enrichedEpisode.getJSONObject("images").getJSONArray("thumbnail").getJSONArray(0).getJSONObject(0).getString("source").equals(NativeArtwork.wide("https://example.invalid/episode1.jpg"))&&enrichedEpisode.getJSONArray("versions").length()==2,"Episode metadata enrichment must retain source audio availability");checks++;
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
        JSONObject highRes=new JSONObject(nullable.toString()).put("coverImage",new JSONObject().put("large","https://example.invalid/small.jpg").put("extraLarge","https://example.invalid/hd.jpg"));
        check(NativeDiscovery.record(highRes).getString("poster").endsWith("/hd.jpg"),"Discovery must select actual extra-large source artwork");checks++;
        Class imageType=Class.forName("com.ellation.crunchyroll.ui.images.CloudflareImagesBuilder$ImageType");Object tallType=java.lang.Enum.valueOf(imageType,"TALL");
        Object builder=Class.forName("com.ellation.crunchyroll.ui.images.CloudflareImagesBuilder").getConstructor(String.class).newInstance("https://unavailable.invalid");
        check(builder.getClass().getMethod("build",String.class,imageType,java.util.List.class).invoke(builder,"ANI154587",tallType,java.util.Collections.emptyList()).equals(title.getString("poster")),"Original keyart URL builder must use replacement artwork instead of nonexistent original CDN IDs");checks++;
        check(NativeArtwork.url("ORIGINAL",tallType)==null,"Original non-adapter image IDs must retain their original mapping");checks++;
        JSONObject clover=new JSONObject(title.toString()).put("anilistId",97940);org.json.JSONArray allEpisodes=new org.json.JSONArray();for(int n=1;n<=170;n++)allEpisodes.put(new JSONObject(providerEpisode.toString()).put("number",n));clover.put("episodeList",allEpisodes);NativeSeasons.apply(clover,null);cached.put(97940,clover);
        JSONObject seasons=NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/cms/series/ANI97940/seasons"));
        check(seasons.getJSONArray("data").length()==4&&seasons.getJSONArray("data").getJSONObject(3).getInt("number_of_episodes")==16,"Black Clover release groups must cover 170 real provider episodes");checks++;
        JSONObject seasonTwo=NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/cms/seasons/ANI97940S2/episodes"));
        check(seasonTwo.getJSONArray("data").length()==51&&seasonTwo.getJSONArray("data").getJSONObject(0).getString("id").equals("ANI97940E52")&&seasonTwo.getJSONArray("data").getJSONObject(0).getJSONArray("versions").getJSONObject(1).getString("season_guid").equals("ANI97940S2"),"Season selection must preserve absolute playback and audio version group IDs");checks++;
        check(NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/discover/up_next/ANI97940E51")).getJSONArray("data").getJSONObject(0).getJSONObject("panel").getJSONObject("episode_metadata").getString("season_id").equals("ANI97940S2"),"Next episode must cross a season boundary without returning Home");checks++;
        check(NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Vietnamese")).equals("vi-VN")&&NativeSubtitles.locale(new JSONObject().put("language","zh-Hant")).equals("zh-TW")&&NativeSubtitles.locale(new JSONObject().put("language","und").put("label","Polish")).equals("pl-PL"),"Additional provider languages must retain usable native locale tags");checks++;
        JSONObject newSummary=new JSONObject(title.toString());newSummary.remove("episodeList");newSummary.put("poster","https://example.invalid/refreshed-hd.jpg");BackendBridge.cache(new org.json.JSONArray().put(newSummary));
        check(BackendBridge.cachedTitle(154587).has("episodeList")&&BackendBridge.cachedTitle(154587).getString("poster").endsWith("refreshed-hd.jpg"),"Artwork refresh must update already-opened series without discarding episodes");checks++;
        for(int n=0;n<10;n++)cached.put(500000+n,new JSONObject(clover.toString()).put("anilistId",500000+n));BackendBridge.trimDetails();
        int detailed=0;for(JSONObject cachedRecord:cached.values())if(cachedRecord.has("episodeList"))detailed++;
        check(detailed<=8&&cached.get(500009).has("episodeList")&&!cached.get(97940).has("episodeList")&&BackendBridge.panel(cached.get(97940)).getJSONObject("series_metadata").getInt("season_count")==4,"Memory eviction must retain recent details and old title/season navigation metadata");checks++;
        java.lang.reflect.Field metadataCache=NativeMetadata.class.getDeclaredField("CACHE");metadataCache.setAccessible(true);java.util.Map memory=(java.util.Map)metadataCache.get(null);
        Class entry=Class.forName("dev.apkforge.bridge.NativeMetadata$Entry");java.lang.reflect.Constructor createEntry=entry.getDeclaredConstructor(JSONObject.class,long.class);createEntry.setAccessible(true);char[] padding=new char[524288];java.util.Arrays.fill(padding,'x');
        for(int n=0;n<8;n++)memory.put("memory-fixture-"+n,createEntry.newInstance(new JSONObject().put("payload",new String(padding)),60000L));NativeMetadata.trimCache();
        long chars=0;java.lang.reflect.Field dataField=entry.getDeclaredField("data");dataField.setAccessible(true);for(Object value:memory.values())chars+=((String)dataField.get(value)).length();
        check(chars<=3*1024*1024&&memory.size()<8,"A few large metadata responses must respect memory budgets before the entry-count limit");memory.clear();checks++;
        check(!BackendBridge.signedIn(new Object()),"Missing app/session context must not grant guest access");checks++;
        int[] panorama=NativeArtwork.cropBounds(2300,450),portrait=NativeArtwork.cropBounds(600,900);
        check(panorama[2]==800&&panorama[3]==450&&panorama[0]==750,"Panorama must be cropped to 16:9, not flattened");checks++;
        check(portrait[2]==600&&portrait[3]==337&&portrait[1]>0,"Portrait fallback preserves proportions with a bounded crop");checks++;
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(2300,450,android.graphics.Bitmap.Config.RGB_565);java.io.ByteArrayOutputStream bitmapBytes=new java.io.ByteArrayOutputStream();bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,bitmapBytes);bitmap.recycle();
        byte[] cropped=NativeArtwork.crop(bitmapBytes.toByteArray());android.graphics.BitmapFactory.Options dimensions=new android.graphics.BitmapFactory.Options();dimensions.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(cropped,0,cropped.length,dimensions);
        check(dimensions.outWidth==800&&dimensions.outHeight==450,"Actual decoded artwork keeps intended dimensions without upscaling");checks++;
        Object sizeModel=Class.forName("com.ellation.crunchyroll.ui.images.BestImageSizeModelUrlImpl").getConstructor(String.class).newInstance(NativeArtwork.wide("https://example.invalid/art.jpg"));
        Object load=NativeArtwork.load(sizeModel,640,360);check(load!=null&&call(load.getClass().getField("c").get(load),"a").equals(java.io.InputStream.class),"Registered wide images use the original Glide data-fetcher contract");checks++;
        JSONObject source2=new JSONObject(title.toString()).put("anilistId",154588).put("poster","https://example.invalid/latest.jpg");
        JSONObject joined=NativeFranchises.merge(java.util.Arrays.asList(title,source2));
        check(joined.getJSONArray("episodeList").length()==4&&NativeSeasons.numbers(joined).size()==2&&joined.getString("poster").endsWith("latest.jpg"),"Verified seasons keep all episodes and latest artwork");checks++;
        JSONObject joinedEpisode=NativeCatalog.episode(joined,joined.getJSONArray("episodeList").getJSONObject(2));
        check(joinedEpisode.getString("id").equals("ANI154588E1")&&joinedEpisode.getString("series_id").equals("ANI154587")&&joinedEpisode.getString("season_id").equals("ANI154587S2"),"Season 2 E1 must resolve its source ID, while navigation uses the common series");checks++;
        cached.put(154587,joined);cached.put(154588,joined);
        check(NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/discover/up_next/ANI154587E2")).getJSONArray("data").getJSONObject(0).getJSONObject("panel").getString("id").equals("ANI154588E1"),"Next must cross different provider series IDs at a season boundary");checks++;
        check(NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/discover/up_next/ANI154588E1D")).getJSONArray("data").getJSONObject(0).getJSONObject("panel").getString("id").equals("ANI154588E2D"),"Next must retain source season and requested dubbed audio");checks++;
        JSONObject firstSeason=new JSONObject().put("anilistId",27000).put("format","TV").put("title","Grouped title").put("status","FINISHED").put("poster","https://example.invalid/first.jpg").put("episodes",new JSONObject().put("total",12)).put("relations",new org.json.JSONArray().put(new JSONObject().put("relationType","SEQUEL").put("node",new JSONObject().put("id",27001).put("format","TV").put("status","FINISHED"))));
        JSONObject secondSeason=new JSONObject(firstSeason.toString()).put("anilistId",27001).put("poster","https://example.invalid/second.jpg").put("relations",new org.json.JSONArray().put(new JSONObject().put("relationType","PREQUEL").put("node",new JSONObject().put("id",27000).put("format","TV").put("status","FINISHED"))));
        org.json.JSONArray collapsed=NativeFranchises.collapse(new org.json.JSONArray().put(secondSeason).put(firstSeason));
        check(collapsed.length()==1&&collapsed.getJSONObject(0).getInt("anilistId")==27000&&collapsed.getJSONObject(0).getJSONObject("episodes").getInt("total")==24,"Search merges actual sequels regardless of result order");checks++;
        JSONObject ova=new JSONObject(firstSeason.toString()).put("format","OVA");check(NativeFranchises.links(ova,"SEQUEL").isEmpty(),"OVAs and movies must not become TV seasons by name guessing");checks++;
        org.json.JSONArray benefits=NativeDownloads.benefits().getJSONArray("items");java.util.List<Object> benefitModels=new java.util.ArrayList<>();for(int i=0;i<benefits.length();i++)benefitModels.add(decode("com.ellation.crunchyroll.api.etp.subscription.model.Benefit",benefits.getJSONObject(i)));
        Class<?> benefitKt=Class.forName("com.ellation.crunchyroll.api.etp.subscription.model.BenefitKt");check((Boolean)benefitKt.getMethod("isAtLeastMegaFanUser",java.util.List.class).invoke(null,benefitModels)&&(Boolean)benefitKt.getMethod("hasOfflineViewingBenefit",java.util.List.class).invoke(null,benefitModels),"Replacement accounts expose full local capabilities without purchasing an official subscription");checks++;
        check(NativeDownloads.variant("https://example.invalid/master.m3u8","#EXTM3U\n#EXT-X-STREAM-INF:RESOLUTION=1280x720\n720.m3u8\n#EXT-X-STREAM-INF:RESOLUTION=640x360\n360.m3u8\n",480).endsWith("360.m3u8"),"Offline quality uses the actual requested HLS variant");checks++;
        NativeDownloads.validate("#EXTM3U\n#EXTINF:10,\na.ts\n#EXT-X-ENDLIST\n");checks++;
        for(String invalid:new String[]{"#EXTM3U\n#EXTINF:10,\na.ts","#EXTM3U\n#EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT=\"com.apple.streamingkeydelivery\"\n#EXT-X-ENDLIST","#EXTM3U\n#EXT-X-KEY:METHOD=AES-128-INVALID\n#EXT-X-ENDLIST"}){try{NativeDownloads.validate(invalid);throw new AssertionError("Unsupported offline manifest accepted");}catch(java.io.IOException expected){}}checks++;
        Object download=decode("com.ellation.crunchyroll.api.etp.download.model.DownloadResponse",NativeDownloads.response("https://example.invalid/360.m3u8",new JSONObject().put("es-ES",new JSONObject().put("locale","es-ES").put("format","ass").put("url","https://example.invalid/sub.ass").put("localFilePath","/data/user/0/test/offline.ass"))));
        check(call(download,"getManifestUrl").equals("https://example.invalid/360.m3u8")&&call(download,"getVideoToken").equals("")&&call(((java.util.Map<?,?>)call(download,"getSubtitles")).get("es-ES"),"getLocalFilePath").equals("/data/user/0/test/offline.ass"),"Original download model retains media URI and local subtitle file");checks++;
        Class<?> managerClass=Class.forName("com.ellation.crunchyroll.downloading.exoplayer.ExoPlayerLocalVideosManagerImpl"),streamClass=Class.forName("com.ellation.crunchyroll.api.cms.model.streams.Stream");
        Class.forName(managerClass.getName()+"$h").getConstructor(String.class,streamClass,managerClass);Class.forName(managerClass.getName()+"$h").getMethod("invoke",Object.class);
        Class.forName(managerClass.getName()+"$i").getConstructor(managerClass,String.class);Class.forName(managerClass.getName()+"$i").getMethod("invoke",Object.class);checks++;
        check(Class.forName("b6.i").getField("b").getType().equals(Class.forName("b6.u"))&&Class.forName("b6.u").getMethod("c",String.class).getReturnType().equals(Class.forName("b6.c"))&&Class.forName("b6.c").getField("b").getType().equals(int.class),"Original offline index exposes the expected completion state contract");checks++;
        check(NativeSubtitles.toAss("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nHola\n").contains("Style: Default,Arial,40,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,-1"),"Separate tracks match bold white text with black outline");checks++;
        System.out.println("Native model contracts passed: "+checks);
        for(String arg:args)if(arg.equals("--franchise-live")){
            JSONObject categories=NativeDiscovery.categories();org.json.JSONArray genres=categories.getJSONArray("data");
            check(genres.length()==17,"All genre routes retained");
            for(int i=0;i<genres.length();i++){
                JSONObject genre=genres.getJSONObject(i);Object original=decode("com.ellation.crunchyroll.model.categories.Category",genre);check(!((java.util.List<?>)call(original,"getBackgrounds")).isEmpty(),"Real genre artwork missing for "+genre.getString("id"));
                JSONObject filtered=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?categories="+genre.getString("id")+"&start=0&n=3"));
                org.json.JSONArray entries=filtered.getJSONArray("data");check(entries.length()>0,"Genre list empty for "+genre.getString("id"));
                String expectedGenre=genre.getJSONObject("localization").getString("title");for(int j=0;j<entries.length();j++){JSONObject cachedTitle=BackendBridge.cachedTitle(Integer.parseInt(entries.getJSONObject(j).getString("id").substring(3)));boolean contains=false;org.json.JSONArray labels=cachedTitle.getJSONArray("genres");for(int g=0;g<labels.length();g++)if(expectedGenre.equals(labels.getString(g)))contains=true;check(contains,"Genre route lost its filter");}
            }
            System.out.println("Live genres passed: 17 artwork models and 17 nonempty correctly filtered lists");
            JSONObject search=NativeDiscovery.route(android.net.Uri.parse("https://local/search?q=one%20punch%20man&n=25"));org.json.JSONArray cards=search.getJSONArray("data").getJSONObject(0).getJSONArray("items");int matching=0;for(int i=0;i<cards.length();i++)if(cards.getJSONObject(i).getString("id").equals("ANI21087"))matching++;check(matching==1,"One-Punch Man root must have one grouped search card");
            JSONObject group=NativeFranchises.series(21087);check(NativeSeasons.numbers(group).size()==3,"One-Punch Man must expose three available seasons");
            for(int season=1;season<=3;season++){JSONObject list=NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/cms/seasons/ANI21087S"+season+"/episodes"));org.json.JSONArray eps=list.getJSONArray("data");check(eps.length()>0,"Season has no episodes");String asset=eps.getJSONObject(0).getString("id");int expected=season==1?21087:season==2?97668:153800;check(asset.equals("ANI"+expected+"E1"),"Wrong first source episode for season "+season);JSONObject ep=NativeCatalog.route(android.net.Uri.parse("https://local/content/v2/cms/episodes/"+asset));check(ep.getJSONArray("data").getJSONObject(0).getString("season_id").equals("ANI21087S"+season),"Episode click lost its group");System.out.println("Season "+season+": "+eps.length()+" real episodes, first source "+asset);}
            JSONObject playback=NativePlayback.resolve("ANI21087E1");String downloadHls=playback.getJSONObject("streams").getJSONObject("adaptive_hls").getJSONObject("").getString("url");String chosen=NativeDownloads.variant(downloadHls,NativePlayback.get(downloadHls,NativePlayback.REFERER),360);NativeDownloads.validate(NativePlayback.get(chosen,NativePlayback.REFERER));
            System.out.println("Live non-DRM HLS download manifest passed; One-Punch Man E1 subtitle locales: "+playback.getJSONObject("subtitles").names());
        }
        for(String arg:args)if(arg.equals("--catalog-live")){
            JSONObject one=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?start=0&n=25")),two=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?start=25&n=25"));
            java.util.HashSet<String> ids=new java.util.HashSet<>();for(int i=0;i<one.getJSONArray("data").length();i++)ids.add(one.getJSONArray("data").getJSONObject(i).getString("id"));
            for(int i=0;i<two.getJSONArray("data").length();i++)check(ids.add(two.getJSONArray("data").getJSONObject(i).getString("id")),"Catalog pages repeat title IDs");
            JSONObject three=NativeDiscovery.route(android.net.Uri.parse("https://local/browse?start=50&n=25"));
            for(int i=0;i<three.getJSONArray("data").length();i++)check(ids.add(three.getJSONArray("data").getJSONObject(i).getString("id")),"Catalog provider page boundary repeats title IDs");
            check(one.getJSONArray("data").length()==25&&two.getJSONArray("data").length()==25,"Catalog page lengths");
            JSONObject home=NativeHomeFeed.home(0,6);int homeTotal=home.getInt("total");java.util.Set<String> rowIds=new java.util.HashSet<>(),cards=new java.util.HashSet<>();int rows=0;
            for(int offset=0;offset<homeTotal;offset+=6){JSONObject chunk=NativeHomeFeed.home(offset,6);check(chunk.getInt("total")==homeTotal,"Home snapshot total changed while paging");org.json.JSONArray data=chunk.getJSONArray("data");
                for(int n=0;n<data.length();n++){JSONObject row=data.getJSONObject(n);check(rowIds.add(row.getString("id")),"Home repeats a row across native pages");
                    Object original=decode("com.ellation.crunchyroll.api.model.HomeFeedItemRaw",row);check((Boolean)call(original,"isValid"),"Live Home row invalid in original model");
                    if(row.optString("resource_type").equals("CURATED_COLLECTION")){rows++;org.json.JSONArray members=row.getJSONArray("ids");check(members.length()<=14,"Home rail exceeds image-work budget");for(int n2=0;n2<members.length();n2++)check(cards.add(members.getString(n2)),"Home repeats anime cards across collections");}
                }
            }
            check(rows>=8&&cards.size()>=60&&NativeHomeFeed.home(homeTotal,6).getJSONArray("data").length()==0,"Live Home must contain diverse rows and terminate cleanly");
            System.out.println("Live Home passed: "+rows+" unique rails, "+cards.size()+" distinct cards, stable paging");
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
