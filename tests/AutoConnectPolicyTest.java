import dev.local.sessioneq.AutoConnectPolicy;
import dev.local.sessioneq.SessionRegistry;
import java.util.*;

public class AutoConnectPolicyTest {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        Set<String> selected=new HashSet<>();selected.add("ru.yandex.music");
        check(AutoConnectPolicy.keepWaiting(true,selected),"ON with selected app must retain the waiting service");
        check(!AutoConnectPolicy.keepWaiting(false,selected),"OFF/STOP must release waiting service without erasing selection");
        check(!AutoConnectPolicy.keepWaiting(true,Collections.emptySet()),"No selections must not retain idle service");
        check(AutoConnectPolicy.acceptOpen(true,selected,"ru.yandex.music",false),"Selected player must connect on a new OPEN");
        check(!AutoConnectPolicy.acceptOpen(true,selected,"com.spotify.music",false),"Unselected app must not attach automatically");
        check(AutoConnectPolicy.acceptOpen(true,selected,"com.spotify.music",true),"Manual connection must retain player integration");
        check(!AutoConnectPolicy.acceptOpen(false,selected,"ru.yandex.music",true),"Incoming OPEN must not override OFF");
        selected.add("com.aimp.player");
        check(!AutoConnectPolicy.acceptOpen(true,selected,"com.aimp.player",true),"AIMP must stay excluded even with stale preferences");
        check(!AutoConnectPolicy.keepWaiting(true,Collections.singleton("com.aimp.player")),"Invalid selection must not retain service");
        SessionRegistry sessions=new SessionRegistry();
        if(AutoConnectPolicy.acceptOpen(true,selected,"ru.yandex.music",false))sessions.remember(101,"ru.yandex.music");
        sessions.forget(101);
        check(sessions.isEmpty()&&AutoConnectPolicy.keepWaiting(true,selected),"Final CLOSE must release identity but retain armed state");
        if(AutoConnectPolicy.acceptOpen(true,selected,"ru.yandex.music",false))sessions.remember(202,"ru.yandex.music");
        check(sessions.snapshot().containsKey(202)&&!sessions.snapshot().containsKey(101),"Restarted player must use new session ID");
        selected.clear();
        check(!AutoConnectPolicy.acceptOpen(true,selected,"ru.yandex.music",false),"Unchecking must prevent next automatic connection");
        System.out.println("Auto-connect: selected players, OFF/STOP, final CLOSE, replacement session and AIMP exclusion passed");
    }
}
