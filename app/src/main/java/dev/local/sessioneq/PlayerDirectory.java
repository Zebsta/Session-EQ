package dev.local.sessioneq;

import android.Manifest;
import android.content.Context;
import android.content.pm.*;
import android.os.Debug;
import android.os.Process;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.*;
import java.util.concurrent.*;

/** On-demand discovery, with no notification listener, root or background timer. */
public final class PlayerDirectory {
    private static final int MAX_DUMP_BYTES=2*1024*1024;
    // At most two workers, created only for a scan and retired after five idle seconds.
    private static final ThreadPoolExecutor DUMP_WORKERS=new ThreadPoolExecutor(
        2,2,5,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(2),task->{
            Thread worker=new Thread(task,"session-eq-audio-dump");worker.setDaemon(true);return worker;
        });
    static {DUMP_WORKERS.allowCoreThreadTimeOut(true);}
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
        List<AudioDumpParser.Track> tracks;
        try {
            String snapshot=readSnapshot("audio");
            if(!AudioDumpParser.recognizes(snapshot))throw new IOException("Неподдерживаемый формат AudioService.");
            tracks=AudioDumpParser.parse(snapshot);
        } catch(IOException|SecurityException primary) {
            // Some firmware dumps call protected routing APIs even for DUMP callers.
            // AudioFlinger's native dump has its own DUMP-only permission check.
            try {
                String snapshot=readSnapshot("media.audio_flinger");
                if(!AudioFlingerParser.recognizes(snapshot))throw new IOException("Неподдерживаемый формат AudioFlinger или отказ DUMP.");
                tracks=AudioFlingerParser.parse(snapshot);
            } catch(IOException|SecurityException fallback) {
                throw new IOException("AudioService: "+primary.getMessage()+"\nAudioFlinger: "+fallback.getMessage(),fallback);
            }
        }
        PackageManager pm=context.getPackageManager();Map<String,Player> players=new LinkedHashMap<>();
        for(AudioDumpParser.Track track:tracks) {
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
    private static String readSnapshot(String serviceName) throws IOException {
        // A system service may not write to an app-private file even with DUMP.
        // Drain a pipe concurrently with the Binder dump, including dumps larger
        // than the pipe buffer, and bound both memory and waiting time.
        ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
        Future<String> reader=null;Future<Boolean> writer=null;
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        try {
            reader=DUMP_WORKERS.submit(()->{
                try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(pipe[0]);
                    ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[8192];int count,total=0;
                    while((count=input.read(buffer))!=-1) {
                        total+=count;
                        if(total>MAX_DUMP_BYTES)throw new IOException("Список звуковых потоков слишком большой.");
                        bytes.write(buffer,0,count);
                    }
                    return new String(bytes.toByteArray(),StandardCharsets.UTF_8);
                }
            });
            writer=DUMP_WORKERS.submit(()->{
                try(ParcelFileDescriptor output=pipe[1]) {
                    return Debug.dumpService(serviceName,output.getFileDescriptor(),new String[0]);
                }
            });
            String snapshot=reader.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS);
            if(!writer.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS))
                throw new IOException("AudioService не предоставил аудиодамп.");
            return snapshot;
        } catch(TimeoutException failure) {
            throw new IOException("AudioService не ответил за 5 секунд. Повтори поиск.",failure);
        } catch(RejectedExecutionException failure) {
            throw new IOException("Предыдущий запрос к AudioService ещё выполняется.",failure);
        } catch(InterruptedException failure) {
            Thread.currentThread().interrupt();throw new IOException("Поиск прерван.",failure);
        } catch(ExecutionException failure) {
            Throwable cause=failure.getCause();
            throw new IOException("Не удалось прочитать AudioService: "+cause.getClass().getSimpleName()+
                (cause.getMessage()==null?"":": "+cause.getMessage()),cause);
        } finally {
            if(reader!=null)reader.cancel(true);
            if(writer!=null)writer.cancel(true);
            DUMP_WORKERS.purge();
            for(ParcelFileDescriptor end:pipe)try{end.close();}catch(IOException ignored){}
        }
    }
}
