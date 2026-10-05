package dev.apkforge.bridge;

import org.json.JSONObject;
import org.json.JSONArray;

/** Maps replacement IDs at the original CDN URL boundary; preserves original image views. */
public final class NativeArtwork {
    private static final String PREFIX="https://appassets.androidplatform.net/apkforge-artwork/";
    private static final java.util.Map<String,String> SOURCES=new java.util.LinkedHashMap<String,String>(256,.75f,true){
        protected boolean removeEldestEntry(java.util.Map.Entry<String,String> e){return size()>2048;}
    };
    static String wide(String source)throws Exception {
        if(source==null||source.isEmpty()||source.startsWith(PREFIX))return source;
        NativePlayback.publicHttps(source);
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder key=new StringBuilder(PREFIX);
        for(byte b:digest)key.append(String.format(java.util.Locale.US,"%02x",b&255));key.append("-16x9-v1.jpg");
        synchronized(SOURCES){SOURCES.put(key.toString(),source);}return key.toString();
    }
    /** Register a Glide fetcher without doing network/bitmap work on the UI thread. */
    @SuppressWarnings({"rawtypes","unchecked"}) public static Object load(Object model,int width,int height){
        try {
            java.lang.reflect.Method size=model.getClass().getMethod("requestCustomSizeUrl",int.class,int.class);size.setAccessible(true);
            String url=(String)size.invoke(model,width,height),source;synchronized(SOURCES){source=SOURCES.get(url);}if(source==null)return null;
            ClassLoader loader=model.getClass().getClassLoader();Class<?> fetcher=Class.forName("com.bumptech.glide.load.data.d",true,loader),callback=Class.forName("com.bumptech.glide.load.data.d$a",true,loader);
            final java.util.concurrent.atomic.AtomicBoolean canceled=new java.util.concurrent.atomic.AtomicBoolean();final String input=source;
            Object proxy=java.lang.reflect.Proxy.newProxyInstance(loader,new Class<?>[]{fetcher},(self,method,args)->{
                switch(method.getName()){
                    case "a":return java.io.InputStream.class;
                    case "d":return Enum.valueOf((Class)Class.forName("mb.a",true,loader),"REMOTE");
                    case "cancel":canceled.set(true);return null;
                    case "b":return null;
                    case "e":
                        try{byte[] jpeg=crop(fetch(input));if(!canceled.get())callback.getMethod("f",Object.class).invoke(args[1],new java.io.ByteArrayInputStream(jpeg));}
                        catch(Exception failure){if(!canceled.get())callback.getMethod("c",Exception.class).invoke(args[1],failure);}return null;
                    case "toString":return "APKForgeArtworkFetcher";
                    case "hashCode":return System.identityHashCode(self);
                    case "equals":return self==args[0];
                    default:throw new UnsupportedOperationException(method.getName());
                }
            });
            Object key=Class.forName("sb.g",true,loader).getConstructor(String.class).newInstance(url);
            return Class.forName("sb.o$a",true,loader).getConstructor(Class.forName("mb.f",true,loader),fetcher).newInstance(key,proxy);
        }catch(Exception unavailable){android.util.Log.w("APKForgeArtwork","Image loader unavailable: "+unavailable.getClass().getSimpleName());return null;}
    }
    static byte[] crop(byte[] bytes)throws Exception {
        android.graphics.BitmapFactory.Options options=new android.graphics.BitmapFactory.Options();options.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(options.outWidth<=0||options.outHeight<=0||options.outWidth>16000||options.outHeight>16000)throw new java.io.IOException("Invalid artwork dimensions");
        int sample=1;while(options.outWidth/sample>2560||options.outHeight/sample>2560)sample*=2;options.inSampleSize=sample;options.inJustDecodeBounds=false;options.inPreferredConfig=android.graphics.Bitmap.Config.RGB_565;
        android.graphics.Bitmap input=android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);if(input==null)throw new java.io.IOException("Invalid artwork");
        int[] rect=cropBounds(input.getWidth(),input.getHeight());android.graphics.Bitmap cropped=android.graphics.Bitmap.createBitmap(input,rect[0],rect[1],rect[2],rect[3]);
        // Do not upscale small source artwork and call it HD.
        int target=Math.min(1280,cropped.getWidth());android.graphics.Bitmap scaled=android.graphics.Bitmap.createScaledBitmap(cropped,target,Math.max(1,target*9/16),true);
        java.io.ByteArrayOutputStream output=new java.io.ByteArrayOutputStream();scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG,94,output);
        if(scaled!=cropped)scaled.recycle();if(cropped!=input)cropped.recycle();input.recycle();return output.toByteArray();
    }
    static int[] cropBounds(int w,int h){int cw=w,ch=h;if((long)w*9>(long)h*16)cw=Math.max(1,h*16/9);else ch=Math.max(1,w*9/16);return new int[]{(w-cw)/2,(h-ch)/2,cw,ch};}
    private static byte[] fetch(String source)throws Exception {
        for(int redirects=0;redirects<4;redirects++){
            NativePlayback.publicHttps(source);java.net.HttpURLConnection c=(java.net.HttpURLConnection)new java.net.URL(source).openConnection();c.setInstanceFollowRedirects(false);c.setConnectTimeout(10000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","APKForge/0.4 (Android)");
            try{int status=c.getResponseCode();if(status>=300&&status<400){source=new java.net.URI(source).resolve(c.getHeaderField("Location")).toString();continue;}if(status!=200)throw new java.io.IOException("Artwork unavailable");
                try(java.io.InputStream in=c.getInputStream();java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>6*1024*1024)throw new java.io.IOException("Artwork too large");out.write(b,0,n);}return out.toByteArray();}}
            finally{c.disconnect();}
        }throw new java.io.IOException("Artwork redirect limit");
    }
    public static String url(String id,Object imageType){
        if(id==null||!id.matches("ANI[0-9]+(?:E[0-9]+D?)?"))return null;
        try{
            String type=(String)imageType.getClass().getMethod("getRawValue").invoke(imageType);
            // Original show-logo components fall back to their original title typography.
            if(type.toLowerCase(java.util.Locale.US).contains("logo"))return "";
            int end=id.indexOf('E');int number=Integer.parseInt(id.substring(3,end<0?id.length():end));
            JSONObject title=BackendBridge.cachedTitle(number);
            if(end<0){JSONObject grouped=NativeFranchises.artwork(number);if(grouped!=null)title=grouped;}
            if(title!=null){
                if(end>0){String raw=id.substring(end+1).replace("D","");JSONArray episodes=title.optJSONArray("episodeList");
                            if(episodes!=null)for(int i=0;i<episodes.length();i++){JSONObject ep=episodes.getJSONObject(i);if(ep.optInt("number")==Integer.parseInt(raw)){String photo=NativeMetadata.string(ep,"thumbnail");if(!photo.isEmpty())return wide(photo);}}}
                String banner=NativeMetadata.string(title,"banner"),poster=NativeMetadata.string(title,"poster");
                if(!type.toLowerCase(java.util.Locale.US).contains("tall"))return wide(banner.isEmpty()?poster:banner);
                if(!poster.isEmpty())return poster;
            }
            return "https://ani.pm/api/anime/cover?anilistId="+number;
        }catch(Exception unavailable){return "";}
    }
}
