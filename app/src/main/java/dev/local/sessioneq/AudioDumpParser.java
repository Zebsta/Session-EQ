package dev.local.sessioneq;

import java.util.*;
import java.util.regex.*;

/** Parse current playback records only; historical START events are never candidates. */
public final class AudioDumpParser {
    public static final class Track {
        public final int sessionId,uid;
        public Track(int sessionId,int uid){this.sessionId=sessionId;this.uid=uid;}
    }
    private AudioDumpParser() {}
    private static final Pattern START=Pattern.compile("(?m)^[\\t ]*AudioPlaybackConfiguration\\b");
    private static final Pattern OWNER=Pattern.compile("\\bu/pid:\\s*(\\d+)\\s*/\\s*\\d+");
    private static final Pattern SESSION=Pattern.compile("\\bsessionId:\\s*(-?\\d+)");
    private static final Pattern STATE=Pattern.compile("\\bstate:\\s*([a-zA-Z_]+)\\b");
    private static final Pattern USAGE=Pattern.compile("\\busage=USAGE_([A-Z_]+)\\b");
    public static boolean recognizes(String dump) {
        return dump.contains("AudioPlaybackConfiguration")||Pattern.compile("(?m)^\\s*players:\\s*$").matcher(dump).find();
    }
    public static List<Track> parse(String dump) {
        int history=dump.indexOf("### Playback activity");
        if(history>=0)dump=dump.substring(0,history);
        List<Integer> starts=new ArrayList<>();Matcher marker=START.matcher(dump);
        while(marker.find())starts.add(marker.start());
        Map<Integer,Track> unique=new LinkedHashMap<>();
        Set<Integer> ambiguous=new HashSet<>();
        for(int i=0;i<starts.size();i++) {
            String record=dump.substring(starts.get(i),i+1<starts.size()?starts.get(i+1):dump.length());
            int section=record.indexOf("\n\n");if(section>=0)record=record.substring(0,section);
            Matcher owner=OWNER.matcher(record),session=SESSION.matcher(record),state=STATE.matcher(record),usage=USAGE.matcher(record);
            if(!state.find()||!state.group(1).equals("started")||!usage.find()||!owner.find()||!session.find())continue;
            if(!Arrays.asList("MEDIA","GAME","UNKNOWN").contains(usage.group(1)))continue;
            try {
                int id=Integer.parseInt(session.group(1)),uid=Integer.parseInt(owner.group(1));
                if(id<=0||uid<10000||ambiguous.contains(id))continue;
                Track previous=unique.get(id);
                if(previous!=null&&previous.uid!=uid){unique.remove(id);ambiguous.add(id);}
                else unique.put(id,new Track(id,uid));
            } catch(NumberFormatException ignored) { /* Ignore malformed records. */ }
        }
        return new ArrayList<>(unique.values());
    }
}
