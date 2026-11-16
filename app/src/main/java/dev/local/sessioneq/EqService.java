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
    public interface Listener {void onStatusChanged();}
    private final Set<Listener> listeners=new LinkedHashSet<>();
    public final class LocalBinder extends Binder {public EqService getService(){return EqService.this;}}
    private final LocalBinder binder=new LocalBinder();
    private boolean foreground;
    private String notificationKey="";
    private boolean destroyed;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private EqState state;
    private String latest="Ожидание аудиосессии. Перезапусти воспроизведение или укажи ID в меню.";

    public static void channel(Context c) {
        NotificationChannel channel=new NotificationChannel(CHANNEL,"Эквалайзер",NotificationManager.IMPORTANCE_LOW);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    @Override public void onCreate() {
        super.onCreate(); instance=this; state=new EqState(this); channel(this);
        record("Экран подключён; аудиосессий пока нет");
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId) {
        if(intent==null) return START_NOT_STICKY;
        if(STOP.equals(intent.getAction())) shutdown();
        return START_NOT_STICKY;
    }
    public void addListener(Listener listener){listeners.add(listener);listener.onStatusChanged();}
    public void removeListener(Listener listener){listeners.remove(listener);}
    public boolean isEnabled(){return state.enabled;}
    public void applySettings(){state=new EqState(this);reconcile();}
    private void ensureForeground() {
        if(foreground)return;
        // Called from a user-visible bound screen, or an already running foreground service.
        startService(new Intent(this,EqService.class).setAction(KEEP_ALIVE));
        try {
            startForeground(1,notification());foreground=true;notificationKey=state.enabled+latest;
        } catch(RuntimeException error){stopSelf();throw error;}
    }
    private void stopForegroundIfIdle() {
        if(!effects.isEmpty())return;
        if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;notificationKey="";}
        // A visible screen can keep a bound service; there is no started idle service.
        stopSelf();
    }
    public void session(Intent intent) {
        int id=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0);
        if(id<=0) { record("Пропущен недопустимый ID сессии: "+id); return; }
        if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
            sessions.forget(id);releaseEffect(id);latest="Сессия "+id+" закрыта";record(latest);
            stopForegroundIfIdle();updateNotification();return;
        }
        String owner=intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        attach(id,owner==null?"Вручную":owner);
    }
    private DynamicsProcessing.Eq eq() {
        DynamicsProcessing.Eq eq=new DynamicsProcessing.Eq(true,true,20);
        for(int i=0;i<20;i++) eq.setBand(i,new DynamicsProcessing.EqBand(true,EqMath.cutoff(i),state.gains[i]));
        return eq;
    }
    public void attach(int id,String owner) {
        state=new EqState(this);
        Integer evicted=sessions.remember(id,owner);
        if(evicted!=null)releaseEffect(evicted);
        reconcile();
    }
    private void createEffect(int id,String owner) {
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
                if(control) { record("Управление сессией "+sessionId+" получено");reconcile(); }
                else { latest="Нет управления сессией "+sessionId+": проверь другие эквалайзеры"; record(latest); updateNotification(); }
            }));
            applyOne(effect);
            latest="Сессия "+id+" · "+owner+" · "+(effect.getEnabled()?"ON":"OFF");
            record(latest+"; каналов="+effect.getChannelCount()+"; полос="+effect.getPreEqByChannelIndex(0).getBandCount());
        } catch(RuntimeException e) {
            if(effect!=null) {effects.remove(id);effect.release();}
            latest="Сессия "+id+": "+e.getClass().getSimpleName()+" · "+e.getMessage();
            record(latest);
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
            for(Integer id:new ArrayList<>(effects.keySet()))releaseEffect(id);
            latest="OFF · Аудиоэффекты освобождены";
            stopForegroundIfIdle();updateNotification();return;
        }
        if(sessions.isEmpty())latest="Ожидание аудиосессии. Запусти плеер или укажи ID в меню.";
        for(Map.Entry<Integer,String> entry:sessions.snapshot().entrySet()) {
            DynamicsProcessing effect=effects.get(entry.getKey());
            if(effect==null){createEffect(entry.getKey(),entry.getValue());continue;}
            try {
                applyOne(effect);
                latest="Сессия "+entry.getKey()+" · "+entry.getValue()+" · "+(effect.getEnabled()?"ON":"OFF");
            } catch(RuntimeException e) {
                latest="Сессия "+entry.getKey()+": "+e.getMessage(); record(latest);
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
    private void record(String message) {
        if(history.size()>=30) history.removeFirst();
        history.addLast(message); Log.i("SessionEQ",message);
    }
    public String status() { return latest; }
    public String diagnostics() {
        StringBuilder b=new StringBuilder("Session EQ 0.4.3\n")
            .append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
            .append(" · Android ").append(Build.VERSION.RELEASE).append("\n\n")
            .append(latest).append("\n\nОбъектов эффектов: ").append(effects.size()).append('\n')
            .append("Фоновая служба: ").append(foreground?"активна для аудиосессии":"не удерживается").append('\n')
            .append("Периодический опрос: нет\nWake lock: нет\n");
        for(Map.Entry<Integer,DynamicsProcessing> e:effects.entrySet()) {
            try {
                DynamicsProcessing fx=e.getValue();
                b.append("ID ").append(e.getKey()).append(" · control=").append(fx.hasControl())
                    .append(" · enabled=").append(fx.getEnabled()).append("\n")
                    .append("Каналы: ").append(fx.getChannelCount()).append(" · Полосы: ")
                    .append(fx.getPreEqByChannelIndex(0).getBandCount()).append("\n")
                    .append("Предпочтительный кадр: ").append(fx.getConfig().getPreferredFrameDuration()).append(" ms\n")
                    .append("Input L/R: ").append(fx.getInputGainByChannelIndex(0)).append(" / ")
                    .append(fx.getInputGainByChannelIndex(Math.min(1,fx.getChannelCount()-1))).append(" dB\n")
                    .append("Полоса 0: ").append(fx.getPreEqBandByChannelIndex(0,0).getGain()).append(" dB\n");
            } catch(RuntimeException error) { b.append(error.toString()).append('\n'); }
        }
        b.append("\nДоступные движки:\n");
        try {
            AudioEffect.Descriptor[] descriptors=AudioEffect.queryEffects();
            if(descriptors!=null) for(AudioEffect.Descriptor d:descriptors)
                if(AudioEffect.EFFECT_TYPE_DYNAMICS_PROCESSING.equals(d.type)) b.append(d.name).append(" · ").append(d.uuid).append('\n');
        } catch(RuntimeException error) { b.append(error.toString()).append('\n'); }
        b.append("\nЖурнал:\n"); for(String line:history) b.append(line).append('\n');
        return b.toString();
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
        state.enabled=false;state.save(this);sessions.clear();
        reconcile();
    }
    @Override public void onDestroy() {
        destroyed=true;handler.removeCallbacksAndMessages(null);
        for(Integer id:new ArrayList<>(effects.keySet()))releaseEffect(id);
        sessions.clear();listeners.clear();
        if(foreground)stopForeground(STOP_FOREGROUND_REMOVE);
        instance=null;super.onDestroy();
    }
    @Override public IBinder onBind(Intent i) { return binder; }
}
