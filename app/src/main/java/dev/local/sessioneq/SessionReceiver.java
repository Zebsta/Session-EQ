package dev.local.sessioneq;

import android.app.*;
import android.content.*;
import android.media.audiofx.AudioEffect;

public class SessionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String owner=intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
        if(SessionPolicy.isBlocked(owner))return;
        if(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())&&!SessionPolicy.mayAttach(owner))return;
        int session=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0);
        if(session<=0) return;
        EqService service=EqService.instance;
        if(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) {
            if(service!=null)service.sessionEvent(intent);
            return;
        }
        if(!AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction()))return;
        EqState settings=new EqState(context);
        if(service!=null&&AutoConnectPolicy.acceptOpen(settings.enabled,settings.autoPlayers,owner,service.hasManualPlayer(owner))) {
            service.sessionEvent(intent);return;
        }
        // OFF must not resurrect a foreground service or prompt for every new song.
        if(!settings.enabled)return;
        // Background receivers cannot reliably launch foreground services on modern Android.
        // A user tap brings the app to the foreground before starting the service.
        EqService.channel(context);
        Intent open=new Intent(context,MainActivity.class).putExtras(intent).setAction(intent.getAction());
        PendingIntent pi=PendingIntent.getActivity(context,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(context,EqService.CHANNEL).setSmallIcon(R.drawable.ic_stat_equalizer)
            .setContentTitle("Подключить Session EQ")
            .setContentText("Нажми, чтобы подключить эквалайзер к плееру")
            .setContentIntent(pi).setAutoCancel(true).build();
        context.getSystemService(NotificationManager.class).notify(2,n);
    }
}
