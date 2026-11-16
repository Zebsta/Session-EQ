package dev.local.sessioneq;

import java.util.*;

/** Session identity is retained while OFF; native effects are managed separately. */
public final class SessionRegistry {
    private final LinkedHashMap<Integer,String> sessions=new LinkedHashMap<>();
    public Integer remember(int id,String owner) {
        if(id<=0)throw new IllegalArgumentException("Session ID must be positive");
        if(!SessionPolicy.mayAttach(owner))throw new IllegalArgumentException("Excluded or unknown player");
        Integer evicted=null;
        if(!sessions.containsKey(id)&&sessions.size()>=8) {
            evicted=sessions.keySet().iterator().next();sessions.remove(evicted);
        }
        sessions.put(id,owner);return evicted;
    }
    public void forget(int id) {sessions.remove(id);}
    public void clear() {sessions.clear();}
    public Map<Integer,String> snapshot() {return new LinkedHashMap<>(sessions);}
    public boolean isEmpty() {return sessions.isEmpty();}
}
