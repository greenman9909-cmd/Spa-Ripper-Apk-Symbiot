package dev.apkforge.bridge;

/** Initializes the retained account-state observer for our replacement account. */
final class NativeAccountState {
    private static Object initialized;
    private static String initializedOwner="";
    private static final java.util.concurrent.atomic.AtomicBoolean queued=new java.util.concurrent.atomic.AtomicBoolean();
    static void initialize(ClassLoader loader){
        if(!CloudSession.emailConfirmed()||!queued.compareAndSet(false,true))return;
        new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
            try{
                Object handler=Class.forName("ch.c$a",true,loader).getField("a").get(null);
                String owner=CloudSession.userId();
                // Clear the original-service reminder only for an actually
                // confirmed Supabase identity, including entry via My Lists.
                if(CloudSession.emailConfirmed()&&handler!=null&&(handler!=initialized||!owner.equals(initializedOwner))){handler.getClass().getMethod("a",java.util.List.class).invoke(handler,java.util.Collections.emptyList());initialized=handler;initializedOwner=owner;}
            }catch(Exception e){android.util.Log.w("APKForgeBridge","Replacement account-state observer unavailable");}
            finally{queued.set(false);}
        });
    }
}
