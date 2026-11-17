import dev.local.sessioneq.AudioDumpParser;
import dev.local.sessioneq.AudioFlingerParser;
import java.util.*;
import java.nio.file.*;

public class AudioFlingerParserTest {
    private static final String HEADER="    Type     Id Active Client(pid/uid) Session Port Id S Flags Format Chn mask SRate ST Usg CT G db\n";
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static String row(String active,int uid,int session,String state,int stream,String usage){
        return "       5430 "+active+" 17158/  "+uid+" "+session+" 6247 "+state+" 0x000 00000001 00000003 44100 "+stream+" "+usage+" 0 0 0\n";
    }
    private static String table(String row){return "Output thread 0x123, name AudioOut_15:\n  1 Tracks of which 1 are active\n"+HEADER+row;}
    public static void main(String[] args)throws Exception {
        String playing=table(row("yes",10375,38689,"A",3,"1"));
        List<AudioDumpParser.Track> tracks=AudioFlingerParser.parse(playing.replace("\n","\r\n"));
        check(tracks.size()==1&&tracks.get(0).sessionId==38689&&tracks.get(0).uid==10375,"Actual Samsung column layout and CRLF");
        check(AudioFlingerParser.parse(table(" F "+row("yes",10375,38689,"A",3,"1").trim()+"\n")).size()==1,"Optional track type");
        check(AudioFlingerParser.parse(table(row("no",10375,38689,"S",3,"1"))).isEmpty(),"Inactive track excluded");
        check(AudioFlingerParser.parse(table(row("yes",10375,38689,"P",3,"1"))).isEmpty(),"Paused state excluded");
        check(AudioFlingerParser.parse(table(row("yes",1000,38689,"A",3,"1"))).isEmpty(),"System UID excluded");
        check(AudioFlingerParser.parse(table(row("yes",10375,0,"A",3,"1"))).isEmpty(),"Session zero excluded");
        check(AudioFlingerParser.parse(table(row("yes",10375,38689,"A",0,"2"))).isEmpty(),"Call stream excluded");
        check(AudioFlingerParser.parse(table(row("yes",10375,38689,"A",3,"d"))).isEmpty(),"Sonification excluded");
        check(AudioFlingerParser.parse(table(row("yes",10375,38689,"A",3,"e"))).size()==1,"Hex game usage");
        check(AudioFlingerParser.parse(playing.replace("Output thread","Record thread")).isEmpty(),"Recording table excluded");
        check(AudioFlingerParser.parse("Output thread 0x1:\n  0 Tracks\nLocal log:\n"+HEADER+row("yes",10375,999,"A",3,"1")).isEmpty(),"Local history excluded");
        check(AudioFlingerParser.parse("Historical Thread Log\n"+playing).isEmpty(),"Historical threads excluded");
        check(AudioFlingerParser.parse(playing+playing).size()==1,"Duplicate session deduplicated");
        check(AudioFlingerParser.parse(playing+table(row("yes",10376,38689,"A",3,"1"))+playing).isEmpty(),"Conflicting owners never guessed");
        check(!AudioFlingerParser.recognizes("Permission Denial: can't dump AudioFlinger"),"Permission denial excluded");
        check(AudioFlingerParser.parse(playing.replace("Client(pid/uid)","Client(pid)")).isEmpty(),"Unknown owner layout excluded");
        if(args.length>0){
            String dump=new String(Files.readAllBytes(Paths.get(args[0])),java.nio.charset.StandardCharsets.UTF_8);
            check(AudioFlingerParser.recognizes(dump),"Device AudioFlinger format recognized");
            System.out.println("Device current music sessions parsed: "+AudioFlingerParser.parse(dump).size());
        }
        System.out.println("AudioFlinger: current output tracks, UID/session identity, state/usage filtering and history exclusion passed");
    }
}
