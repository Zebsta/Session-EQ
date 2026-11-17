package dev.local.sessioneq;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class EqState {
    public final float[] gains = new float[20];
    public float preamp, balance;
    public boolean enabled, auto;
    public boolean limiter = true;
    public String preset = "Rock";
    public final Set<String> autoPlayers=new HashSet<>();
    public EqState(Context context) {
        SharedPreferences p = context.getSharedPreferences("eq", 0);
        for(int i=0;i<20;i++) gains[i]=EqMath.clamp(p.getFloat("band"+i,EqMath.ROCK[i]),-15,15);
        preamp=EqMath.clamp(p.getFloat("preamp",0),-15,15);
        balance=EqMath.clamp(p.getFloat("balance",0),-1,1);
        enabled=p.getBoolean("enabled",false);
        auto=p.getBoolean("auto",false);
        limiter=p.getBoolean("limiter",true);
        preset=p.getString("preset","Rock");
        for(String owner:p.getStringSet("autoPlayers",new HashSet<String>()))
            if(SessionPolicy.mayAttach(owner))autoPlayers.add(owner);
    }
    public void save(Context context) {
        SharedPreferences.Editor e=context.getSharedPreferences("eq",0).edit();
        for(int i=0;i<20;i++) e.putFloat("band"+i,gains[i]);
        e.putFloat("preamp",preamp).putFloat("balance",balance).putBoolean("enabled",enabled)
            .putBoolean("auto",auto).putBoolean("limiter",limiter).putString("preset",preset)
            .remove("manualSession").putStringSet("autoPlayers",new HashSet<>(autoPlayers)).apply();
    }
}
