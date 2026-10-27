package com.opencheps.util;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "opencheps";
    public  static final String KEY_USER_SIDE = "user_side";
    public  static final String KEY_THINK_MS  = "think_ms";
    public  static final String KEY_MULTIPV   = "multipv";

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static String userSide(Context c) {
        return get(c).getString(KEY_USER_SIDE, "w");
    }

    public static void setUserSide(Context c, String side) {
        get(c).edit().putString(KEY_USER_SIDE, side).apply();
    }

    public static int thinkMs(Context c) { return Math.max(500,Math.min(15000,get(c).getInt(KEY_THINK_MS, 1500))); }
    public static int multiPv(Context c) { return Math.max(1,Math.min(3,get(c).getInt(KEY_MULTIPV, 1))); }
    public static void saveBoard(Context c,android.graphics.Rect r,int w,int h) {
        SharedPreferences.Editor e=get(c).edit();
        if(r==null)e.remove("board_region");
        else e.putString("board_region",w+","+h+","+r.left+","+r.top+","+r.width());
        e.apply();
    }
    public static android.graphics.Rect loadBoard(Context c,int w,int h) {
        String raw=get(c).getString("board_region",null);if(raw==null)return null;
        try{String[] f=raw.split(",");if(f.length!=5||Integer.parseInt(f[0])!=w||Integer.parseInt(f[1])!=h)return null;
            int x=Integer.parseInt(f[2]),y=Integer.parseInt(f[3]),s=Integer.parseInt(f[4]);
            return x>=0&&y>=0&&s>=64&&x+s<=w&&y+s<=h?new android.graphics.Rect(x,y,x+s,y+s):null;
        }catch(NumberFormatException e){return null;}
    }
}

