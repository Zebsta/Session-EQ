package dev.local.sessioneq;

import java.util.*;
import java.util.regex.*;

/** Current playback tables only; never parse AudioFlinger's historical/local logs. */
public final class AudioFlingerParser {
    private AudioFlingerParser() {}
    private static final Pattern TABLE=Pattern.compile("\\d+ Tracks(?: of which \\d+ are active)?");
    private static final Pattern ROW=Pattern.compile(
        "^(?:[A-Za-z]+\\s+)?\\d+\\s+(yes|no)\\s+\\d+\\s*/\\s*(\\d+)\\s+(\\d+)\\s+\\d+\\s+([A-Za-z])\\s+"+
        "\\S+\\s+\\S+\\s+\\S+\\s+\\d+\\s+(\\d+)\\s+([0-9a-fA-F]+)\\s+[0-9a-fA-F]+(?:\\s.*)?$");
    public static boolean recognizes(String dump) {
        return dump.contains("Output thread ")&&!dump.contains("Permission Denial");
    }
    public static List<AudioDumpParser.Track> parse(String dump) {
        Map<Integer,AudioDumpParser.Track> unique=new LinkedHashMap<>();
        Set<Integer> ambiguous=new HashSet<>();
        boolean output=false,table=false,header=false;
        for(String raw:dump.split("\\r?\\n")) {
            String line=raw.trim();
            if(line.startsWith("Historical Thread Log"))break;
            if(line.startsWith("Output thread ")){output=true;table=false;header=false;continue;}
            if(line.startsWith("Record thread ")||line.startsWith("Input thread ")||line.startsWith("Mmap thread ")||line.startsWith("## ")) {
                output=false;table=false;header=false;continue;
            }
            if(!output)continue;
            if(TABLE.matcher(line).matches()){table=true;header=false;continue;}
            if(!table)continue;
            if(!header) {
                header=line.startsWith("Type ")&&line.contains("Client(pid/uid)")&&line.contains("Session")&&line.matches(".*\\bST\\s+Usg\\s+CT\\b.*");
                if(!header)table=false;
                continue;
            }
            Matcher row=ROW.matcher(line);
            if(!row.matches()){table=false;continue;}
            if(!row.group(1).equals("yes")||!row.group(4).equals("A"))continue;
            try {
                int uid=Integer.parseInt(row.group(2)),id=Integer.parseInt(row.group(3));
                int stream=Integer.parseInt(row.group(5)),usage=Integer.parseInt(row.group(6),16);
                if(uid<10000||id<=0||stream!=3||(usage!=0&&usage!=1&&usage!=14)||ambiguous.contains(id))continue;
                AudioDumpParser.Track previous=unique.get(id);
                if(previous!=null&&previous.uid!=uid){unique.remove(id);ambiguous.add(id);}
                else unique.put(id,new AudioDumpParser.Track(id,uid));
            }catch(NumberFormatException ignored){}
        }
        return new ArrayList<>(unique.values());
    }
}
