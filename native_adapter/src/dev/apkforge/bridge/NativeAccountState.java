package dev.apkforge.bridge;

/** Initializes the retained account-state observer for our replacement account. */
final class NativeAccountState {
    private static Object initialized;
    private static final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    static void initialize(ClassLoader loader){
        if(!queued.compareAndSet(false,true))return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            try{
                Object handler=Class.forName("ch.c$a",true,loader).getField("a").get(null);
                // The guest has no email. Supabase password sign-in is verified
                // by Supabase. Neither account uses original-service email hints.
                if(handler!=null&&handler!=initialized){handler.getClass().getMethod("a",java.util.List.class).invoke(handler,java.util.Collections.emptyList());initialized=handler;}
            }catch(Exception e){android.util.Log.w("APKForgeBridge","Replacement account-state observer unavailable");}
            finally{queued.set(false);}
        });
    }
}
