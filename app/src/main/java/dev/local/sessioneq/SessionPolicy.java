package dev.local.sessioneq;

/** Shared by discovery, broadcasts, activity intents, the registry and native attachment. */
public final class SessionPolicy {
    private SessionPolicy() {}
    public static boolean isBlocked(String name) {
        if(name!=null)name=name.trim();
        return name!=null&&(name.equals("com.aimp.player")||name.startsWith("com.aimp.player.")
            ||name.equals("dev.local.sessioneq"));
    }
    public static boolean mayAttach(String name) {
        return name!=null&&!name.trim().isEmpty()&&!isBlocked(name);
    }
    public static String singleOwner(String[] packages) {
        return packages!=null&&packages.length==1&&mayAttach(packages[0])?packages[0]:null;
    }
}
