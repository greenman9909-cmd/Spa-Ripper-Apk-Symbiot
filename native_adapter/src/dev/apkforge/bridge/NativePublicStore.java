package dev.apkforge.bridge;

import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded private cache of public catalog JSON only; never sessions or media URLs. */
final class NativePublicStore {
    static final long MAX_AGE=7L*86400000;
    private static final Object LOCK=new Object();
    static File directory(){try{File folder=new File(CloudSession.context(NativePublicStore.class.getClassLoader()).getCacheDir(),"apkforge-public-v1");return folder.isDirectory()||folder.mkdirs()?folder:null;}catch(Exception unavailable){return null;}}
    private static File file(File folder,String key)throws Exception {
        byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));StringBuilder name=new StringBuilder();for(byte b:digest)name.append(String.format(Locale.US,"%02x",b&255));return new File(folder,name+".json");
    }
    static JSONObject read(File folder,String key){
        if(folder==null)return null;
        synchronized(LOCK){try{File f=file(folder,key);if(!f.isFile()||f.length()>5*1024*1024)return null;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new android.util.AtomicFile(f).openRead()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(bytes.size()+n>5*1024*1024)return null;bytes.write(b,0,n);}}
            JSONObject entry=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));long age=System.currentTimeMillis()-entry.getLong("savedAt");
            if(age<0||age>MAX_AGE){f.delete();return null;}entry.getJSONObject("value");return entry;
        }catch(Exception corrupt){return null;}}
    }
    static void write(File folder,String key,JSONObject value){
        if(folder==null)return;
        synchronized(LOCK){try{byte[] bytes=new JSONObject().put("savedAt",System.currentTimeMillis()).put("value",value).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>5*1024*1024)return;
            android.util.AtomicFile target=new android.util.AtomicFile(file(folder,key));FileOutputStream out=null;try{out=target.startWrite();out.write(bytes);target.finishWrite(out);}catch(Exception failed){if(out!=null)target.failWrite(out);return;}
            File[] files=folder.listFiles((dir,name)->name.matches("[a-f0-9]{64}\\.json"));if(files==null)return;Arrays.sort(files,Comparator.comparingLong(File::lastModified));long total=0;for(File f:files)total+=f.length();int count=files.length;
            for(File f:files){if(total<=24*1024*1024&&count<=96)break;long length=f.length();if(f.delete()){total-=length;count--;}}
        }catch(Exception unavailable){android.util.Log.w("APKForgeMetadata","Public disk cache unavailable");}}
    }
}
