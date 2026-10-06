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
        protected boolean removeEldestEntry(Map.Entry<String,Track> e){return size()>512;}
    };
    private static final class Track {final String source;volatile String ass;Track(String value){source=value;}}
    private static final Pattern TIMING=Pattern.compile("^((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}\\.[0-9]{3})\\s+-->\\s+((?:[0-9]{2,}:)?[0-9]{2}:[0-9]{2}\\.[0-9]{3})(?:\\s+.*)?$");
    static String register(String source)throws Exception {
        NativePlayback.publicHttps(source);
        synchronized(TRACKS){
            String existing=null;for(Map.Entry<String,Track> item:TRACKS.entrySet())if(item.getValue().source.equals(source)){existing=item.getKey();break;}if(existing!=null){TRACKS.get(existing);return existing;}
            String local=PREFIX+UUID.randomUUID()+".ass";TRACKS.put(local,new Track(source));return local;
        }
    }
    static String content(String url)throws Exception {
        Track track;synchronized(TRACKS){track=TRACKS.get(url);}if(track==null)throw new IOException("Unknown subtitle");
        synchronized(track){if(track.ass==null)track.ass=toAss(NativePlayback.get(track.source,NativePlayback.REFERER));String value=track.ass;trim(track);return value;}
    }
    private static void trim(Track keep){synchronized(TRACKS){long chars=0;for(Track t:TRACKS.values())if(t.ass!=null)chars+=t.ass.length();for(Track t:TRACKS.values()){if(chars<=2*1024*1024)break;if(t!=keep&&t.ass!=null){chars-=t.ass.length();t.ass=null;}}}}
    public static WebResourceResponse intercept(WebResourceRequest request){
        String url=request.getUrl().toString();if(!url.startsWith(PREFIX))return null;
        Track track;synchronized(TRACKS){track=TRACKS.get(url);}
        try {
            if(track==null||!"GET".equals(request.getMethod()))return response(404,"Not Found","");
            String content;
            // This callback runs on WebView's request worker. Fetch only when a track is selected.
            content=content(url);
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
        if(language.equals("zh-hant")||label.contains("chinese")&&label.contains("traditional"))return "zh-TW";
        if(language.equals("zh-hans"))return "zh-CN";
        if(label.contains("spanish")&&(label.contains("latin")||label.contains("latam")))return "es-419";
        if(label.contains("portuguese")&&label.contains("brazil"))return "pt-BR";
        if(language.isEmpty()||language.equals("und")){
            String[] names={"english","spanish","portuguese","french","german","italian","arabic","russian","japanese","korean","chinese","hindi","turkish","indonesian","thai","vietnamese","polish","dutch","ukrainian","hebrew","bengali","tamil","telugu","malay","persian","swedish","danish","norwegian","finnish","romanian","hungarian","czech","greek","bulgarian","serbian"};
            String[] tags={"en","es","pt","fr","de","it","ar","ru","ja","ko","zh","hi","tr","id","th","vi","pl","nl","uk","he","bn","ta","te","ms","fa","sv","da","no","fi","ro","hu","cs","el","bg","sr"};
            language="";for(int i=0;i<names.length;i++)if(label.startsWith(names[i])){language=tags[i];break;}
        }
        if(!language.matches("[a-z]{2,3}(?:-(?:[a-z]{2}|[0-9]{3}))?"))return "";
        if(language.contains("-")){String[] parts=language.split("-");return parts[0]+"-"+parts[1].toUpperCase(Locale.US);}
        String[] base={"en","es","pt","fr","de","it","ar","ru","ja","ko","zh","hi","tr","id","th","vi","pl","nl","uk","he","bn","ta","te","ms","fa","sv","da","no","fi","ro","hu","cs","el","bg","sr"};
        String[] nativeTags={"en-US","es-ES","pt-PT","fr-FR","de-DE","it-IT","ar-SA","ru-RU","ja-JP","ko-KR","zh-CN","hi-IN","tr-TR","id-ID","th-TH","vi-VN","pl-PL","nl-NL","uk-UA","he-IL","bn-BD","ta-IN","te-IN","ms-MY","fa-IR","sv-SE","da-DK","no-NO","fi-FI","ro-RO","hu-HU","cs-CZ","el-GR","bg-BG","sr-RS"};
        for(int i=0;i<base.length;i++)if(language.equals(base[i]))return nativeTags[i];
        return language.equals("und")?"":language;
    }
    static String toAss(String vtt)throws IOException {
        String normalized=vtt.replace("\r\n","\n").replace('\r','\n');if(normalized.startsWith("\ufeff"))normalized=normalized.substring(1);
        if(normalized.length()>1048576)throw new IOException("Subtitle too large");
        if(normalized.startsWith("[Script Info]"))normalized=assToVtt(normalized);
        else if(!normalized.startsWith("WEBVTT")&&Pattern.compile("(?m)^[0-9]{2}:[0-9]{2}:[0-9]{2},[0-9]{3}\\s+-->").matcher(normalized).find())normalized="WEBVTT\n\n"+normalized.replaceAll("([0-9]{2}:[0-9]{2}:[0-9]{2}),([0-9]{3})","$1.$2");
        if(!normalized.startsWith("WEBVTT")||normalized.length()>1048576)throw new IOException("Invalid WebVTT subtitle");
        StringBuilder out=new StringBuilder("[Script Info]\nScriptType: v4.00+\nPlayResX: 1280\nPlayResY: 720\nWrapStyle: 0\n\n[V4+ Styles]\nFormat: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\nStyle: Default,Arial,40,&H00FFFFFF,&H00FFFFFF,&H00000000,&H80000000,-1,0,0,0,100,100,0,0,1,2.5,1,2,36,36,36,1\n\n[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n");
        int count=0;
        for(String block:normalized.split("\n[ \\t]*\n")){
            String[] lines=block.split("\n");if(lines.length==0||lines[0].startsWith("NOTE")||lines[0].startsWith("STYLE")||lines[0].startsWith("REGION")||lines[0].startsWith("WEBVTT"))continue;
            int index=lines[0].contains("-->")?0:1;if(index>=lines.length)continue;
            Matcher match=TIMING.matcher(lines[index].trim());if(!match.matches())continue;
            long start,end;try{start=milliseconds(match.group(1));end=milliseconds(match.group(2));}catch(IOException|NumberFormatException malformed){continue;}if(end<=start||end>86400000)continue;
            StringBuilder cue=new StringBuilder();for(int i=index+1;i<lines.length;i++){if(cue.length()>0)cue.append("\n");cue.append(lines[i]);}
            String text=cue.toString().replaceAll("<[^>]*>","").replace("&lt;","<").replace("&gt;",">").replace("&nbsp;"," ").replace("&quot;","\"").replace("&#39;","'").replace("&amp;","&");
            // Prevent subtitle data becoming libass overrides. Keep Unicode and line breaks.
            text=text.replace("\\","／").replace("{","｛").replace("}","｝").replace("\n","\\N");
            if(text.trim().isEmpty())continue;
            out.append("Dialogue: 0,").append(assTime(start)).append(',').append(assTime(end)).append(",Default,,0,0,0,,").append(text).append('\n');count++;
        }
        if(count==0)throw new IOException("No timed subtitle cues");return out.toString();
    }
    static boolean supported(String format,String path){String f=format.toLowerCase(Locale.US),p=path.toLowerCase(Locale.US);for(String kind:new String[]{"vtt","srt","ass","ssa"})if(f.equals(kind)||p.endsWith("."+kind))return true;return f.equals("webvtt")||f.equals("text/vtt")||f.equals("application/x-subrip")||f.equals("text/x-ssa")||f.equals("text/x-ass");}
    private static String assToVtt(String ass)throws IOException {
        StringBuilder out=new StringBuilder("WEBVTT\n\n");boolean events=false;String[] fields=null;
        for(String line:ass.split("\n")){line=line.trim();if(line.startsWith("[")){events=line.equalsIgnoreCase("[Events]");continue;}if(!events)continue;
            if(line.startsWith("Format:")){fields=line.substring(7).toLowerCase(Locale.US).split(",");continue;}if(!line.startsWith("Dialogue:")||fields==null||fields.length>32)continue;
            String[] values=line.substring(9).split(",",fields.length);if(values.length!=fields.length)continue;String start="",end="",text="";
            for(int i=0;i<fields.length;i++){String key=fields[i].trim();if(key.equals("start"))start=values[i].trim();else if(key.equals("end"))end=values[i].trim();else if(key.equals("text"))text=values[i];}
            if(start.isEmpty()||end.isEmpty()||text.isEmpty())continue;
            try{out.append(vttTime(start)).append(" --> ").append(vttTime(end)).append('\n').append(text.replaceAll("\\{[^}]*\\}","").replace("\\N","\n").replace("\\n","\n").replace("\\h"," ")).append("\n\n");}catch(IOException malformed){}
        }return out.toString();
    }
    private static String vttTime(String value)throws IOException {Matcher m=Pattern.compile("^([0-9]{1,2}):([0-9]{2}):([0-9]{2})[.,]([0-9]{1,3})$").matcher(value);if(!m.matches())throw new IOException("Invalid ASS timestamp");return String.format(Locale.US,"%02d:%s:%s.%s",Integer.parseInt(m.group(1)),m.group(2),m.group(3),(m.group(4)+"000").substring(0,3));}
    private static long milliseconds(String value)throws IOException {
        String[] p=value.replace('.',':').split(":");int h=p.length==4?Integer.parseInt(p[0]):0,offset=p.length==4?1:0;
        int m=Integer.parseInt(p[offset]),s=Integer.parseInt(p[offset+1]),ms=Integer.parseInt(p[offset+2]);if(m>59||s>59||h>24)throw new IOException("Invalid cue timestamp");return ((h*60L+m)*60+s)*1000+ms;
    }
    private static String assTime(long ms){long cs=ms/10;return String.format(Locale.US,"%d:%02d:%02d.%02d",cs/360000,(cs/6000)%60,(cs/100)%60,cs%100);}
}
