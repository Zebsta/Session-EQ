import dev.local.sessioneq.SessionPolicy;
import dev.local.sessioneq.SessionRegistry;

public class SessionPolicyTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(SessionPolicy.mayAttach("com.aimp.player"),"AIMP must be permitted in comparison build");
        check(SessionPolicy.mayAttach("com.aimp.player.beta"),"AIMP variant must be permitted");
        check(SessionPolicy.mayAttach(" com.aimp.player "),"AIMP remains permitted with trimmed package input");
        check(!SessionPolicy.mayAttach(null)&&!SessionPolicy.mayAttach(""),"Unknown owners cannot auto-attach");
        check(SessionPolicy.mayAttach("ru.yandex.music"),"Yandex must remain allowed");
        check(SessionPolicy.mayAttach("Вручную"),"Previous explicit manual flow must remain available");
        SessionRegistry registry=new SessionRegistry();registry.remember(100,"ru.yandex.music");
        try {registry.remember(101,"dev.local.sessioneq");throw new AssertionError("Registry accepted self");}
        catch(IllegalArgumentException expected){}
        registry.remember(102,"com.aimp.player");
        check(registry.snapshot().size()==2,"Rejecting self must preserve existing sessions");
        System.out.println("Session policy: comparison AIMP permitted; self and unknown owners excluded");
    }
}
