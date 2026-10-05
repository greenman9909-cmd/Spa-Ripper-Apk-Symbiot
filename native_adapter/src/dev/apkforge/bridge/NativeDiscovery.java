package dev.apkforge.bridge;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Calendar;
import java.util.Locale;

/** Paged AniList discovery, with ani.pm retained as the episode availability source. */
final class NativeDiscovery {
    private static final int PAGE_SIZE=50;
    private static final String[] GENRES={"Action","Adventure","Comedy","Drama","Fantasy","Horror","Mahou Shoujo","Mecha","Music","Mystery","Psychological","Romance","Sci-Fi","Slice of Life","Sports","Supernatural","Thriller"};
    private static final String[] SEASONS={"WINTER","SPRING","SUMMER","FALL"};
    private static final String QUERY="query($page:Int,$search:String,$genres:[String],$sort:[MediaSort],$season:MediaSeason,$year:Int,$status:MediaStatus){Page(page:$page,perPage:50){pageInfo{hasNextPage}media(type:ANIME,isAdult:false,search:$search,genre_in:$genres,sort:$sort,season:$season,seasonYear:$year,status:$status){id idMal title{english romaji native}description(asHtml:false) coverImage{large}bannerImage episodes genres status season seasonYear isAdult}}}";
    static boolean handles(String path){return path.endsWith("/search")||path.endsWith("/browse")||path.endsWith("/browse/index")||path.contains("/categories")||path.endsWith("/seasonal_tags");}
    static int integer(String value,int fallback){try{return Integer.parseInt(value);}catch(Exception e){return fallback;}}
    static JSONObject variables(Uri uri)throws Exception {
        JSONObject vars=new JSONObject();String q=uri.getQueryParameter("q");if(q!=null&&!q.trim().isEmpty())vars.put("search",q.trim());
        String sort=uri.getQueryParameter("sort_by");
        vars.put("sort",new JSONArray().put("alphabetical".equals(sort)?"TITLE_ROMAJI":"newly_added".equals(sort)?"START_DATE_DESC":"POPULARITY_DESC").put("ID"));
        String cats=uri.getQueryParameter("categories");if(cats!=null&&!cats.isEmpty()){
            JSONArray genres=new JSONArray();for(String category:cats.split(",")){
                boolean found=false;for(String genre:GENRES)if(category.equals(genreId(genre))||category.equalsIgnoreCase(genre)){genres.put(genre);found=true;break;}
                if(!found)throw new BackendBridge.HttpFailure(400,"unknown-category");
            }vars.put("genres",genres);
        }
        String season=uri.getQueryParameter("seasonal_tag");
        if("current".equals(season))vars.put("status","RELEASING");
        else if(season!=null&&!season.isEmpty()){
            if(!season.matches("[12][0-9]{3}-(winter|spring|summer|fall)"))throw new BackendBridge.HttpFailure(400,"invalid-season");
            vars.put("year",Integer.parseInt(season.substring(0,4))).put("season",season.substring(5).toUpperCase(Locale.US));
        }
        return vars;
    }
    static JSONObject route(Uri uri)throws Exception {
        String path=uri.getPath();if(path.contains("/categories"))return categories();
        if(path.endsWith("/seasonal_tags"))return seasons();
        // Section-index UI has no trustworthy global counts from AniList. Retain
        // one full-catalog section; the list itself fetches additional real pages.
        if(path.endsWith("/browse/index"))return BackendBridge.envelope(new JSONArray().put(new JSONObject().put("prefix","").put("offset",0).put("total",1).put("title","All Anime")));
        int start=Math.max(0,integer(uri.getQueryParameter("start"),0)),limit=Math.max(1,Math.min(100,integer(uri.getQueryParameter("n"),25)));
        if(start>100000)throw new BackendBridge.HttpFailure(400,"catalog-offset-too-large");
        JSONObject vars=variables(uri);JSONArray items=new JSONArray();int cursor=start,total=start;
        boolean more=true;
        while(items.length()<limit&&more){
            int page=cursor/PAGE_SIZE+1,offset=cursor%PAGE_SIZE;vars.put("page",page);
            JSONObject result=NativeMetadata.graph(QUERY,vars).getJSONObject("Page");JSONArray media=result.getJSONArray("media");
            more=result.getJSONObject("pageInfo").optBoolean("hasNextPage");
            JSONArray records=new JSONArray();for(int i=0;i<media.length();i++)records.put(record(media.getJSONObject(i)));BackendBridge.cache(records);
            for(int i=offset;i<records.length()&&items.length()<limit;i++){items.put(BackendBridge.panel(records.getJSONObject(i)));cursor++;}
            total=lowerBound(page,records.length(),more);
            if(offset>=records.length()||records.length()<PAGE_SIZE)break;
        }
        JSONObject response=BackendBridge.envelope(items).put("total",total).put("meta",new JSONObject().put("has_next_page",cursor<total));
        if(path.endsWith("/search"))return BackendBridge.envelope(new JSONArray().put(new JSONObject().put("type","series").put("count",total).put("items",items))).put("total",total);
        return response;
    }
    static JSONArray feed(String id)throws Exception {
        JSONObject vars=variables(Uri.parse("https://local/browse?seasonal_tag="+(id.equals("airing")?"current":currentSeasonId())));vars.put("page",1);
        JSONArray media=NativeMetadata.graph(QUERY,vars).getJSONObject("Page").getJSONArray("media"),items=new JSONArray();
        for(int i=0;i<Math.min(20,media.length());i++)items.put(record(media.getJSONObject(i)));return items;
    }
    static String currentSeasonId(){Calendar now=Calendar.getInstance();return now.get(Calendar.YEAR)+"-"+SEASONS[now.get(Calendar.MONTH)/3].toLowerCase(Locale.US);}
    static int lowerBound(int page,int size,boolean hasNext){return (page-1)*PAGE_SIZE+size+(hasNext?1:0);}
    static JSONObject record(JSONObject media)throws Exception {
        JSONObject title=media.getJSONObject("title");String name=NativeMetadata.string(title,"english");if(name.isEmpty())name=NativeMetadata.string(title,"romaji");
        String description=NativeMetadata.string(media,"description").replaceAll("(?i)<br\\s*/?>","\n").replaceAll("<[^>]*>","");
        return new JSONObject().put("anilistId",media.getInt("id")).put("malId",media.optInt("idMal"))
            .put("title",name).put("nativeTitle",NativeMetadata.string(title,"native")).put("synopsis",description)
            .put("poster",media.getJSONObject("coverImage").optString("large","")).put("banner",media.opt("bannerImage"))
            .put("genres",media.optJSONArray("genres")).put("status",media.optString("status")).put("adult",media.optBoolean("isAdult"))
            .put("episodes",new JSONObject().put("total",media.optInt("episodes"))).put("metadataOnly",true);
    }
    static String genreId(String genre){return genre.toLowerCase(Locale.US).replace(' ','-');}
    static JSONObject categories()throws Exception {
        JSONArray items=new JSONArray();for(String genre:GENRES)items.put(new JSONObject().put("id",genreId(genre))
            .put("localization",new JSONObject().put("title",genre).put("description","").put("locale","en-US")));
        return BackendBridge.envelope(items);
    }
    static JSONObject seasons()throws Exception {
        JSONArray items=new JSONArray().put(new JSONObject().put("id","current").put("localization",new JSONObject().put("title","Currently Airing")));
        Calendar now=Calendar.getInstance();int year=now.get(Calendar.YEAR),quarter=now.get(Calendar.MONTH)/3;
        for(int i=0;i<8;i++){String season=SEASONS[quarter];items.put(new JSONObject().put("id",year+"-"+season.toLowerCase(Locale.US))
            .put("localization",new JSONObject().put("title",season.charAt(0)+season.substring(1).toLowerCase(Locale.US)+" "+year)));if(--quarter<0){quarter=3;year--;}}
        return BackendBridge.envelope(items);
    }
}
