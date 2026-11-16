package dev.local.sessioneq;

import android.app.*;
import android.content.*;
import android.media.audiofx.AudioEffect;

public class SessionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        int session=intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0);
        if(session<=0) return;
        if(EqService.instance!=null) { EqService.instance.session(intent); return; }
        if(!AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION.equals(intent.getAction())) return;
        // OFF must not resurrect a foreground service or prompt for every new song.
        if(!new EqState(context).enabled)return;
        // Background receivers cannot reliably launch foreground services on modern Android.
        // A user tap brings the app to the foreground before starting the service.
        EqService.channel(context);
        Intent open=new Intent(context,MainActivity.class).putExtras(intent).setAction(intent.getAction());
        PendingIntent pi=PendingIntent.getActivity(context,2,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(context,EqService.CHANNEL).setSmallIcon(R.drawable.ic_stat_equalizer)
            .setContentTitle("Подключить Session EQ")
            .setContentText("Нажми для подключения к аудиосессии "+session)
            .setContentIntent(pi).setAutoCancel(true).build();
        context.getSystemService(NotificationManager.class).notify(2,n);
    }
}
