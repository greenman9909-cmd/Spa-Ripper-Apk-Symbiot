package dev.apkforge.bridge;

import org.json.JSONObject;

/** The original episode controls use thumbs, while series use five stars. */
final class NativeRatings {
    static JSONObject container(boolean episode,String rating)throws Exception {
        if(episode){
            String value=rating.toUpperCase(java.util.Locale.ROOT);
            boolean up=value.equals("UP"),down=value.equals("DOWN");
            return new JSONObject().put("up",new JSONObject().put("displayed",up?1:0))
                .put("down",new JSONObject().put("displayed",down?1:0)).put("total",up||down?1:0)
                .put("rating",up||down?value:"NONE");
        }
        int vote=rating.matches("[1-5]s")?Integer.parseInt(rating.substring(0,1)):0;
        JSONObject result=new JSONObject().put("average",vote).put("total",vote==0?0:1).put("rating",rating);
        for(int i=1;i<=5;i++)result.put(i+"s",new JSONObject().put("displayed",vote==i?1:0).put("percentage",vote==i?100:0));
        return result;
    }
}
