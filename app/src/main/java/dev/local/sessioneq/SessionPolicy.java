package dev.local.sessioneq;

/** Prevent cascading Session EQ with AIMP's internal equalizer. */
public final class SessionPolicy {
    private SessionPolicy() {}
    public static boolean isBlocked(String owner) {
        if(owner==null)return false;
        owner=owner.trim();
        return owner.equals("com.aimp.player")||owner.startsWith("com.aimp.player.")
            ||owner.equals("dev.local.sessioneq");
    }
    public static boolean mayAttach(String owner) {
        return owner!=null&&!owner.trim().isEmpty()&&!isBlocked(owner);
    }
    public static String singleOwner(String[] packages) {
        return packages!=null&&packages.length==1&&mayAttach(packages[0])?packages[0]:null;
    }
}
