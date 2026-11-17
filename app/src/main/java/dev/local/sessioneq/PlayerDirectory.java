package dev.local.sessioneq;

import android.Manifest;
import android.content.Context;
import android.content.pm.*;
import android.os.Debug;
import android.os.Process;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.*;

/** On-demand discovery, with no notification listener, root or background timer. */
public final class PlayerDirectory {
    private static final int MAX_DUMP_BYTES=2*1024*1024;
    public static final class Player {
        public final String packageName,label;
        public final int uid;
        public final List<Integer> sessionIds=new ArrayList<>();
        Player(String packageName,String label,int uid){this.packageName=packageName;this.label=label;this.uid=uid;}
        public Map<Integer,String> sessions() {
            Map<Integer,String> out=new LinkedHashMap<>();
            for(int id:sessionIds)out.put(id,packageName);return out;
        }
    }
    private PlayerDirectory() {}
    public static boolean hasAccess(Context context) {
        return context.checkSelfPermission(Manifest.permission.DUMP)==PackageManager.PERMISSION_GRANTED;
    }
    public static List<Player> query(Context context) throws IOException {
        if(!hasAccess(context))throw new SecurityException("DUMP permission required");
        String snapshot=readSnapshot(context);
        if(!AudioDumpParser.recognizes(snapshot))throw new IOException("Android не предоставил список звуковых потоков в поддерживаемом формате.");
        PackageManager pm=context.getPackageManager();Map<String,Player> players=new LinkedHashMap<>();
        for(AudioDumpParser.Track track:AudioDumpParser.parse(snapshot)) {
            if(track.uid==Process.myUid()||track.uid/100000!=Process.myUid()/100000)continue;
            String[] names=pm.getPackagesForUid(track.uid);
            // Shared UIDs cannot reliably identify one player; do not guess.
            String name=SessionPolicy.singleOwner(names);if(name==null)continue;
            try {
                ApplicationInfo info=pm.getApplicationInfo(name,0);if(info.uid!=track.uid)continue;
                Player player=players.get(name);
                if(player==null){player=new Player(name,pm.getApplicationLabel(info).toString(),track.uid);players.put(name,player);}
                if(!player.sessionIds.contains(track.sessionId))player.sessionIds.add(track.sessionId);
            }catch(PackageManager.NameNotFoundException ignored) { /* App may have exited. */ }
        }
        List<Player> out=new ArrayList<>(players.values());Collator collator=Collator.getInstance();
        out.sort((a,b)->collator.compare(a.label,b.label));return out;
    }
    private static String readSnapshot(Context context) throws IOException {
        // A private temporary file avoids blocking the synchronous binder dump on
        // an unread pipe. The snapshot is discarded and never included in logs.
        File file=File.createTempFile("session-eq-audio-", ".tmp",context.getCacheDir());
        try {
            try(FileOutputStream output=new FileOutputStream(file)) {
                if(!Debug.dumpService("audio",output.getFD(),new String[0]))
                    throw new IOException("Не удалось получить список звуковых потоков.");
            }
            if(file.length()>MAX_DUMP_BYTES)throw new IOException("Список звуковых потоков слишком большой.");
            try(InputStream input=new FileInputStream(file);ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=input.read(buffer))!=-1) {
                    total+=count;if(total>MAX_DUMP_BYTES)throw new IOException("Список звуковых потоков слишком большой.");
                    bytes.write(buffer,0,count);
                }
                return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
            }
        } finally {if(!file.delete())file.deleteOnExit();}
    }
}
