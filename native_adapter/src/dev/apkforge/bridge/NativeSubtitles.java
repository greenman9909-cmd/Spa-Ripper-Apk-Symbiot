package dev.apkforge.bridge;

import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

/** Adapts public WebVTT to the retained local libass renderer, without executing provider pages. */
public final class NativeSubtitles {
    private static final String PREFIX="https://appassets.androidplatform.net/apkforge-subtitles/";
    private static final Map<String,Track> TRACKS=new LinkedHashMap<String,Track>(64,.75f,true){
        protected boolean removeEldestEntry(Map.Entry<String,Track> e){return size()>128;}
    };
    private static final class Track {final String source;String ass;Track(String value){source=value;}}
    private static final Pattern TIMING=Pattern.compile("^((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}\\.[0-9]{3})\\s+-->\\s+((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}\\.[0-9]{3})(?:\\s+.*)?$");
    static String register(String source)throws Exception {
        NativePlayback.publicHttps(source);
        synchronized(TRACKS){
            for(Map.Entry<String,Track> item:TRACKS.entrySet())if(item.getValue().source.equals(source))return item.getKey();
            String local=PREFIX+UUID.randomUUID()+".ass";TRACKS.put(local,new Track(source));return local;
        }
    }
    public static WebResourceResponse intercept(WebResourceRequest request){
        String url=request.getUrl().toString();if(!url.startsWith(PREFIX))return null;
        Track track;synchronized(TRACKS){track=TRACKS.get(url);}
        try {
            if(track==null||!"GET".equals(request.getMethod()))return response(404,"Not Found","");
            String content;
            // This callback runs on WebView's request worker. Fetch only when a track is selected.
            synchronized(track){if(track.ass==null)track.ass=toAss(NativePlayback.get(track.source,NativePlayback.REFERER));content=track.ass;}
            android.util.Log.i("APKForgeNative","Subtitle cues ready for retained renderer");
            return response(200,"OK",content);
        }catch(Exception unavailable){android.util.Log.w("APKForgeNative","Subtitle source unavailable: "+unavailable.getClass().getSimpleName());return response(502,"Subtitle Unavailable","");}
    }
    private static WebResourceResponse response(int status,String reason,String value){
        Map<String,String> headers=new java.util.HashMap<>();headers.put("Cache-Control","no-store");headers.put("X-Content-Type-Options","nosniff");
        return new WebResourceResponse("text/plain","UTF-8",status,reason,headers,new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    static String locale(JSONObject track){
        String label=NativeMetadata.string(track,"label").toLowerCase(Locale.US);
        String language=NativeMetadata.string(track,"language");if(language.isEmpty())language=NativeMetadata.string(track,"srclang");
        if(language.isEmpty())language=NativeMetadata.string(track,"locale");
        language=language.replace('_','-').toLowerCase(Locale.US);
        if(label.contains("spanish")&&(label.contains("latin")||label.contains("latam")))return "es-419";
        if(label.contains("portuguese")&&label.contains("brazil"))return "pt-BR";
        if(language.isEmpty()||language.equals("und")){
            String[] names={"english","spanish","portuguese","french","german","italian","arabic","russian","japanese","korean","chinese","hindi","turkish","indonesian","thai"};
            String[] tags={"en","es","pt","fr","de","it","ar","ru","ja","ko","zh","hi","tr","id","th"};
            language="";for(int i=0;i<names.length;i++)if(label.startsWith(names[i])){language=tags[i];break;}
        }
        if(!language.matches("[a-z]{2,3}(?:-(?:[a-z]{2}|[0-9]{3}))?"))return "";
        if(language.contains("-")){String[] parts=language.split("-");return parts[0]+"-"+parts[1].toUpperCase(Locale.US);}
        String[] base={"en","es","pt","fr","de","it","ar","ru","ja","ko","zh","hi","tr","id","th"};
        String[] nativeTags={"en-US","es-ES","pt-PT","fr-FR","de-DE","it-IT","ar-SA","ru-RU","ja-JP","ko-KR","zh-CN","hi-IN","tr-TR","id-ID","th-TH"};
        for(int i=0;i<base.length;i++)if(language.equals(base[i]))return nativeTags[i];
        return language.equals("und")?"":language;
    }
    static String toAss(String vtt)throws IOException {
        String normalized=vtt.replace("\r\n","\n").replace('\r','\n');if(normalized.startsWith("\ufeff"))normalized=normalized.substring(1);
        if(!normalized.startsWith("WEBVTT")||normalized.length()>1048576)throw new IOException("Invalid WebVTT subtitle");
        StringBuilder out=new StringBuilder("[Script Info]\nScriptType: v4.00+\nPlayResX: 1280\nPlayResY: 720\nWrapStyle: 0\n\n[V4+ Styles]\nFormat: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\nStyle: Default,Arial,42,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,0,0,0,0,100,100,0,0,1,2,1,2,30,30,32,1\n\n[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n");
        int count=0;
        for(String block:normalized.split("\n[ \\t]*\n")){
            String[] lines=block.split("\n");if(lines.length==0||lines[0].startsWith("NOTE")||lines[0].startsWith("STYLE")||lines[0].startsWith("REGION")||lines[0].startsWith("WEBVTT"))continue;
            int index=lines[0].contains("-->")?0:1;if(index>=lines.length)continue;
            Matcher match=TIMING.matcher(lines[index].trim());if(!match.matches())continue;
            long start=milliseconds(match.group(1)),end=milliseconds(match.group(2));if(end<=start||end>86400000)continue;
            StringBuilder cue=new StringBuilder();for(int i=index+1;i<lines.length;i++){if(cue.length()>0)cue.append("\n");cue.append(lines[i]);}
            String text=cue.toString().replaceAll("<[^>]*>","").replace("&lt;","<").replace("&gt;",">").replace("&nbsp;"," ").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");
            // Prevent subtitle data becoming libass overrides. Keep Unicode and line breaks.
            text=text.replace("\\","／").replace("{","｛").replace("}","｝").replace("\n","\\N");
            if(text.trim().isEmpty())continue;
            out.append("Dialogue: 0,").append(assTime(start)).append(',').append(assTime(end)).append(",Default,,0,0,0,,").append(text).append('\n');count++;
        }
        if(count==0)throw new IOException("No timed subtitle cues");return out.toString();
    }
    private static long milliseconds(String value)throws IOException {
        String[] p=value.replace('.',':').split(":");int h=p.length==4?Integer.parseInt(p[0]):0,offset=p.length==4?1:0;
        int m=Integer.parseInt(p[offset]),s=Integer.parseInt(p[offset+1]),ms=Integer.parseInt(p[offset+2]);if(m>59||s>59||h>24)throw new IOException("Invalid cue timestamp");return ((h*60L+m)*60+s)*1000+ms;
    }
    private static String assTime(long ms){long cs=ms/10;return String.format(Locale.US,"%d:%02d:%02d.%02d",cs/360000,(cs/6000)%60,(cs/100)%60,cs%100);}
}
