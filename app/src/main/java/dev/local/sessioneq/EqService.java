package dev.local.sessioneq;

import android.app.*;
import android.content.*;
import android.media.audiofx.*;
import android.os.*;
import android.util.Log;
import java.util.*;

public class EqService extends Service {
    public static EqService instance;
    public static final String STOP="dev.local.sessioneq.STOP";
    private static final String KEEP_ALIVE="dev.local.sessioneq.KEEP_ALIVE";
    public static final String CHANNEL="session-eq";
    private final LinkedHashMap<Integer, DynamicsProcessing> effects=new LinkedHashMap<>();
    private final SessionRegistry sessions=new SessionRegistry();
    private final Set<String> manualPlayers=new HashSet<>();
    public interface Listener {void onStatusChanged();}
    private final Set<Listener> listeners=new LinkedHashSet<>();
    public final class LocalBinder extends Binder {public EqService getService(){return EqService.this;}}
    private final LocalBinder binder=new LocalBinder();
    private boolean foreground;
    private String notificationKey="";
    private boolean destroyed;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private EqState state;
    private String latest="Выбери проигрыватель в меню подключения.";

    public static void channel(Context c) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Эквалайзер",NotificationManager.IMPORTANCE_LOW);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public void onCreate() {
        super.onCreate(); instance=this; state=new EqState(this); channel(this);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent!=null&&STOP.equals(intent.getAction())) {shutdown();return START_NOT_STICKY;}
        state=new EqState(this);
        reconcile();
        return AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers)?START_STICKY:START_NOT_STICKY;
    }
    public void addListener(Listener listener){listeners.add(listener);listener.onStatusChanged();}
    public void removeListener(Listener listener){listeners.remove(listener);}
    public boolean isEnabled(){return state.enabled;}
    public boolean hasManualPlayer(String owner){return manualPlayers.contains(owner);}
    public boolean connectSelected(Map<Integer,String> selected) {
        if(selected.isEmpty())return false;
        for(Map.Entry<Integer,String> entry:selected.entrySet())
            if(entry.getKey()<=0||!SessionPolicy.mayAttach(entry.getValue()))return false;
        for(Integer id:new ArrayList<>(effects.keySet()))releaseEffect(id);
        sessions.clear();manualPlayers.clear();manualPlayers.addAll(selected.values());
        for(Map.Entry<Integer,String> entry:selected.entrySet())sessions.remember(entry.getKey(),entry.getValue());
        state=new EqState(this);reconcile();return !effects.isEmpty();
    }
    public void applySettings(){
        boolean waiting=AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers);
        state=new EqState(this);
        // Refresh Android's restart mode when arming/disarming an already active service.
        if(foreground&&waiting!=AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers))
            startService(new Intent(this,EqService.class).setAction(KEEP_ALIVE));
        reconcile();
    }
    private void ensureForeground() {
        if(foreground)return;
        // Called from a user-visible bound screen, or an already running foreground service.
        startService(new Intent(this,EqService.class).setAction(KEEP_ALIVE));
        getSystemService(NotificationManager.class).cancel(2);
        try {
            startForeground(1,notification());foreground=true;notificationKey=state.enabled+latest;
        } catch(RuntimeException error){stopSelf();throw error;}
    }
    private void stopForegroundIfIdle() {
        if(!effects.isEmpty())return;
        if(AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers)) {ensureForeground();return;}
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;notificationKey="";}
        // A visible screen can keep a bound service; there is no started idle service.
        stopSelf();
    }
    public void session(Intent intent) {handleSession(intent,true);}
    public void sessionEvent(Intent intent) {handleSession(intent,false);}
    private void handleSession(Intent intent,boolean explicit) {
        String owner=intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        if(SessionPolicy.isBlocked(owner))return;
        int id=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0);
        if(id<=0)return;
        if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
            String current=sessions.snapshot().get(id);
            if(current!=null&&owner!=null&&!owner.equals(current))return;
            sessions.forget(id);manualPlayers.retainAll(sessions.snapshot().values());
            releaseEffect(id);
            if(!effects.isEmpty())latest="Подключено: "+playerName(sessions.snapshot().get(effects.keySet().iterator().next()));
            else latest=AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers)
                ?"Автоподключение · ожидание музыки":"Воспроизведение завершено";
            stopForegroundIfIdle();updateNotification();return;
        }
        if(!SessionPolicy.mayAttach(owner))return;
        if(explicit)manualPlayers.add(owner);
        attach(id,owner);
    }
    private DynamicsProcessing.Eq eq() {
        DynamicsProcessing.Eq eq=new DynamicsProcessing.Eq(true,true,20);
        for(int i=0;i<20;i++) eq.setBand(i,new DynamicsProcessing.EqBand(true,EqMath.cutoff(i),state.gains[i]));
        return eq;
    }
    public void attach(int id,String owner) {
        if(id<=0||!SessionPolicy.mayAttach(owner))return;
        state=new EqState(this);
        Integer evicted=sessions.remember(id,owner);
        if(evicted!=null){releaseEffect(evicted);manualPlayers.retainAll(sessions.snapshot().values());}
        reconcile();
    }
    private void createEffect(int id,String owner) {
        if(!SessionPolicy.mayAttach(owner))return;
        DynamicsProcessing effect=null;
        try {
            ensureForeground();
            DynamicsProcessing.Config config=new DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,2,true,20,false,0,false,0,true)
                    // Prefer frequency resolution for closely spaced bass bands in a music player.
                    .setPreferredFrameDuration(80f)
                    .setPreEqAllChannelsTo(eq())
                    .setLimiterAllChannelsTo(limiter()).build();
            for(int ch=0;ch<2;ch++) config.setInputGainByChannelIndex(ch,input(ch));
            effect=new DynamicsProcessing(0,id,config);
            if(!effect.hasControl()) throw new IllegalStateException("эффектом управляет другое приложение");
            effects.put(id,effect);
            final int sessionId=id;
            effect.setControlStatusListener((fx,control)->handler.post(()->{
                if(destroyed||effects.get(sessionId)!=fx)return;
                if(control)reconcile();
                else {latest="Другой эквалайзер управляет звуком. Проверь настройки плеера.";updateNotification();}
            }));
            applyOne(effect);
            latest="Подключено: "+playerName(owner);
        } catch(RuntimeException e) {
            if(effect!=null) {effects.remove(id);effect.release();}
            Log.w("SessionEQ","Не удалось подключить аудиоэффект",e);
            latest="Не удалось подключить эквалайзер к "+playerName(owner)+". Проверь настройки плеера.";
        }
    }
    private DynamicsProcessing.Limiter limiter() {
        // Stereo-linked limiter, no makeup gain. Its response differs from AIMP.
        return new DynamicsProcessing.Limiter(true,state.limiter,0,1f,60f,100f,-1f,0f);
    }
    private float input(int channel) { return EqMath.inputGain(state.preamp,state.balance,channel,state.auto,state.gains); }
    private void applyOne(DynamicsProcessing effect) {
        if(!effect.hasControl()) throw new IllegalStateException("нет управления эффектом");
        if(!state.enabled) {
            int result=effect.setEnabled(false);
            if(result!=AudioEffect.SUCCESS) throw new IllegalStateException("setEnabled: "+result);
            return;
        }
        effect.setPreEqAllChannelsTo(eq());
        effect.setLimiterAllChannelsTo(limiter());
        for(int ch=0;ch<effect.getChannelCount();ch++) effect.setInputGainbyChannel(ch,input(ch%2));
        int result=effect.setEnabled(true);
        if(result!=AudioEffect.SUCCESS) throw new IllegalStateException("setEnabled: "+result);
        if(!effect.getEnabled()) throw new IllegalStateException("система не включила эффект");
    }
    private void reconcile() {
        if(!state.enabled) {
            getSystemService(NotificationManager.class).cancel(2);
            for(Integer id:new ArrayList<>(effects.keySet()))releaseEffect(id);
            latest="Эквалайзер выключен";
            stopForegroundIfIdle();updateNotification();return;
        }
        if(sessions.isEmpty())latest=AutoConnectPolicy.keepWaiting(state.enabled,state.autoPlayers)
            ?"Автоподключение · ожидание музыки":"Выбери проигрыватель в меню подключения.";
        for(Map.Entry<Integer,String> entry:sessions.snapshot().entrySet()) {
            DynamicsProcessing effect=effects.get(entry.getKey());
            if(effect==null){createEffect(entry.getKey(),entry.getValue());continue;}
            try {
                applyOne(effect);
                latest="Подключено: "+playerName(entry.getValue());
            } catch(RuntimeException e) {
                Log.w("SessionEQ","Не удалось применить настройки эквалайзера",e);
                latest="Не удалось применить настройки. Проверь другие эквалайзеры.";
            }
        }
        stopForegroundIfIdle();
        updateNotification();
    }
    private void releaseEffect(int id) {
        DynamicsProcessing effect=effects.remove(id);
        if(effect!=null) {
            try {if(effect.hasControl())effect.setEnabled(false);}
            catch(RuntimeException error){Log.w("SessionEQ","Отключение эффекта перед освобождением",error);}
            finally {effect.setControlStatusListener(null);effect.release();}
        }
    }
    public String status() {return latest;}
    private String playerName(String owner) {
        if(owner==null)return "проигрыватель";
        try {
            return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(owner,0)).toString();
        } catch(android.content.pm.PackageManager.NameNotFoundException ignored) {return "проигрыватель";}
    }
    private Notification notification() {
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,EqService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this,CHANNEL).setSmallIcon(dev.local.sessioneq.R.drawable.ic_stat_equalizer)
            .setContentTitle("Session EQ · "+(state!=null&&state.enabled?"ON":"OFF"))
            .setContentText(latest).setContentIntent(pi).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(new Notification.Action.Builder(null,"Остановить",stop).build()).build();
    }
    private void updateNotification() {
        if(destroyed)return;
        String key=state.enabled+latest;
        if(foreground&&!key.equals(notificationKey)) {
            getSystemService(NotificationManager.class).notify(1,notification());notificationKey=key;
        }
        for(Listener listener:new ArrayList<>(listeners))listener.onStatusChanged();
    }
    public void shutdown() {
        state=new EqState(this);state.enabled=false;state.save(this);sessions.clear();manualPlayers.clear();
        getSystemService(NotificationManager.class).cancel(2);
        reconcile();
    }
    @Override public void onDestroy() {
        destroyed=true;handler.removeCallbacksAndMessages(null);
        for(Integer id:new ArrayList<>(effects.keySet()))releaseEffect(id);
        sessions.clear();manualPlayers.clear();listeners.clear();
        if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);
        instance=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return binder; }
}
