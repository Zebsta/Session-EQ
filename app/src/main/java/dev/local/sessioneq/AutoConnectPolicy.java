package dev.local.sessioneq;

import java.util.Set;

/** Pure decisions shared by receiver and service; no polling or session IDs persisted. */
public final class AutoConnectPolicy {
    private AutoConnectPolicy() {}
    public static boolean keepWaiting(boolean enabled,Set<String> players) {
        if(!enabled)return false;
        for(String owner:players)if(SessionPolicy.mayAttach(owner))return true;
        return false;
    }
    public static boolean acceptOpen(boolean enabled,Set<String> players,String owner,boolean connected) {
        return enabled&&SessionPolicy.mayAttach(owner)&&(players.contains(owner)||connected);
    }
}
