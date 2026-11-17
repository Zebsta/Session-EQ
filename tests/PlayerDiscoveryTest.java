import dev.local.sessioneq.AudioDumpParser;
import dev.local.sessioneq.SessionPolicy;
import dev.local.sessioneq.SessionRegistry;
import java.util.*;

public class PlayerDiscoveryTest {
    private static void check(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static String track(int session,int uid,String state,String usage){
        return "  AudioPlaybackConfiguration piid:42 deviceIds:[3] type:android.media.AudioTrack u/pid:"+uid+"/5678 state:"+state+
            " attr:AudioAttributes: usage=USAGE_"+usage+" content=CONTENT_TYPE_MUSIC flags=0xA00 tags= bundle=null sessionId:"+session+" mutedState:streamVolume\n";
    }
    public static void main(String[] args) {
        String dump="PlaybackActivityMonitor\n  players:\n\n"+track(101,10375,"started","MEDIA")+
            track(102,10376,"paused","MEDIA")+track(103,1000,"started","MEDIA")+
            track(104,10377,"started","ASSISTANCE_SONIFICATION")+track(0,10378,"started","MEDIA")+
            track(-1,10379,"started","MEDIA")+track(101,10375,"started","MEDIA")+
            "\n### Playback activity\n"+track(900,10380,"started","MEDIA");
        List<AudioDumpParser.Track> parsed=AudioDumpParser.parse(dump);
        check(parsed.size()==1&&parsed.get(0).sessionId==101&&parsed.get(0).uid==10375,
            "Must return active unique music tracks, not history, PID, system tones, paused or invalid sessions");
        String wrapped="players:\n"+track(201,10375,"started","MEDIA").replace(" attr:","\n    attr:");
        check(AudioDumpParser.parse(wrapped).size()==1,"Wrapped records must parse");
        check(AudioDumpParser.parse(track(202,10375,"paused","MEDIA")+"new event state:started sessionId:999\n").isEmpty(),
            "Historical START after paused record must not reactivate it");
        check(AudioDumpParser.recognizes("players:\n"),"Empty current-player list is valid");
        check(!AudioDumpParser.recognizes("Permission Denial: cannot dump audio"),"Permission denial is not empty success");
        check(AudioDumpParser.parse(track(203,10375,"started","UNKNOWN")).size()==1,"Default-attribute music players are supported");
        check(AudioDumpParser.parse(track(204,10375,"started","VOICE_COMMUNICATION")).isEmpty(),"Calls must be excluded");
        check(AudioDumpParser.parse(track(205,10375,"started","MEDIA")+track(205,10376,"started","MEDIA")+
            track(205,10375,"started","MEDIA")).isEmpty(),"A session shared by different owners must never be guessed");
        check(SessionPolicy.mayAttach("com.aimp.player"),"AIMP permitted for test");
        check(SessionPolicy.mayAttach("com.aimp.player.beta"),"AIMP variant permitted for test");
        check(SessionPolicy.mayAttach(" com.aimp.player "),"Trimmed AIMP permitted for test");
        check(!SessionPolicy.mayAttach(null)&&!SessionPolicy.mayAttach(""),"Unknown owners cannot be attached");
        check(!SessionPolicy.mayAttach("dev.local.sessioneq"),"Self excluded");
        check(SessionPolicy.mayAttach("ru.yandex.music")&&SessionPolicy.mayAttach("com.spotify.music"),"Other players allowed");
        check(SessionPolicy.singleOwner(new String[]{"ru.yandex.music"}).equals("ru.yandex.music"),"Resolve known owner");
        check("com.aimp.player".equals(SessionPolicy.singleOwner(new String[]{"com.aimp.player"})),"AIMP must be listed in comparison build");
        check(SessionPolicy.singleOwner(new String[]{"ru.yandex.music","com.aimp.player"})==null,"Shared AIMP UID excluded");
        check(SessionPolicy.singleOwner(null)==null&&SessionPolicy.singleOwner(new String[0])==null,"Unresolved UID excluded");
        SessionRegistry registry=new SessionRegistry();registry.remember(500,"ru.yandex.music");
        try {registry.remember(501,"dev.local.sessioneq");throw new AssertionError("Registry accepted self");}
        catch(IllegalArgumentException expected){}
        check(registry.snapshot().size()==1,"Blocked owner must not disturb an allowed session");
        System.out.println("Player discovery: active/current parsing, wrapped records, stale/invalid/call exclusion and AIMP policy passed");
    }
}
