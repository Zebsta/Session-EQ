import dev.local.sessioneq.SessionRegistry;
import java.util.*;

public class SessionRegistryTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        SessionRegistry sessions=new SessionRegistry();
        check(sessions.isEmpty(),"Launch must have no sessions");
        sessions.remember(35473,"Yandex");
        Map<Integer,String> snapshot=sessions.snapshot();
        // OFF releases native resources elsewhere, but a still-open identity must survive for ON.
        check(sessions.snapshot().get(35473).equals("Yandex"),"Open session must survive without native resources");
        snapshot.clear();
        check(!sessions.isEmpty(),"UI snapshot must not mutate service state");
        sessions.remember(40000,"Other player");
        sessions.forget(35473);sessions.forget(35473);
        check(sessions.snapshot().size()==1&&sessions.snapshot().containsKey(40000),"Repeated CLOSE must preserve other player");
        sessions.forget(40000);
        check(sessions.isEmpty(),"Last CLOSE must allow service shutdown");
        for(int i=1;i<=8;i++)check(sessions.remember(i,"Player") == null,"Premature eviction");
        check(sessions.remember(8,"Updated owner")==null,"Duplicate OPEN must not evict another session");
        Integer evicted=sessions.remember(9,"New player");
        check(Integer.valueOf(1).equals(evicted),"Owner of evicted native effect must be returned for release");
        check(sessions.snapshot().size()==8&&!sessions.snapshot().containsKey(1),"Session storage must remain bounded");
        sessions.clear();check(sessions.isEmpty(),"STOP must forget all identities");
        try {sessions.remember(0,"Invalid");throw new AssertionError("Session zero accepted");}
        catch(IllegalArgumentException expected){}
        System.out.println("Session registry: OFF/ON identity, repeated CLOSE, multiple players, eviction and STOP passed");
    }
}
