import dev.local.sessioneq.SessionPolicy;
import dev.local.sessioneq.SessionRegistry;

public class SessionPolicyTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args) {
        check(!SessionPolicy.mayAttach("com.aimp.player"),"AIMP must be blocked");
        check(!SessionPolicy.mayAttach("com.aimp.player.beta"),"AIMP variant must be blocked");
        check(!SessionPolicy.mayAttach(" com.aimp.player "),"Whitespace cannot bypass exclusion");
        check(!SessionPolicy.mayAttach(null)&&!SessionPolicy.mayAttach(""),"Unknown owners cannot auto-attach");
        check(SessionPolicy.mayAttach("ru.yandex.music"),"Yandex must remain allowed");
        check(SessionPolicy.mayAttach("Вручную"),"Previous explicit manual flow must remain available");
        SessionRegistry registry=new SessionRegistry();registry.remember(100,"ru.yandex.music");
        try {registry.remember(101,"com.aimp.player");throw new AssertionError("Registry accepted AIMP");}
        catch(IllegalArgumentException expected){}
        check(registry.snapshot().size()==1,"Rejecting AIMP must preserve existing sessions");
        System.out.println("Session policy: AIMP excluded; previous Yandex/manual connection preserved");
    }
}
