package dev.local.sessioneq;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.audiofx.AudioEffect;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity {
    private EqState state;
    private EqView view;
    private boolean stopping;
    private boolean bound,dirty;
    private EqService service;
    private final ArrayDeque<Intent> pendingSessions=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable apply=this::flushChanges;
    private final EqService.Listener statusListener=this::serviceChanged;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            service=((EqService.LocalBinder)binder).getService();
            flushChanges();service.addListener(statusListener);
            while(!pendingSessions.isEmpty())service.session(pendingSessions.removeFirst());
        }
        @Override public void onServiceDisconnected(ComponentName name) {
            service=null;view.setStatus("Служба остановлена");
        }
    };
    private void flushChanges() {
        if(!dirty)return;
        state.save(this);dirty=false;
        EqService target=service!=null?service:EqService.instance;
        if(target!=null)target.applySettings();
    }
    private void serviceChanged() {
        if(service==null||stopping)return;
        if(!dirty)state.enabled=service.isEnabled();
        view.setStatus(service.status());view.invalidate();
    }
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        int theme=base.getSharedPreferences("appearance",0).getInt("theme",0);
        if(theme!=0) {
            Configuration override=new Configuration();
            override.uiMode=theme==1?Configuration.UI_MODE_NIGHT_NO:Configuration.UI_MODE_NIGHT_YES;
            applyOverrideConfiguration(override);
        }
    }
    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        state=new EqState(this);view=new EqView(this,state);
        getWindow().setStatusBarColor(getColor(R.color.eq_background));
        getWindow().setNavigationBarColor(getColor(R.color.eq_toolbar));
        setContentView(view);
        // Target 35 enforces edge-to-edge. Keep the drawing outside system bars.
        if(Build.VERSION.SDK_INT>=35) view.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)v.getLayoutParams();
            if(lp.leftMargin!=bars.left||lp.topMargin!=bars.top||lp.rightMargin!=bars.right||lp.bottomMargin!=bars.bottom){
                lp.setMargins(bars.left,bars.top,bars.right,bars.bottom);v.setLayoutParams(lp);
            }
            return insets;
        });
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);
        consume(getIntent());
    }
    private void consume(Intent source) {
        if(source!=null&&source.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION,0)>0) {
            queueSession(new Intent(source));
        }
    }
    private void queueSession(Intent command) {
        if(service!=null){flushChanges();service.session(command);}
        else {if(pendingSessions.size()>=8)pendingSessions.removeFirst();pendingSessions.addLast(command);}
    }
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);consume(intent);}
    @Override protected void onStart(){
        super.onStart();bound=bindService(new Intent(this,EqService.class),connection,Context.BIND_AUTO_CREATE);
    }
    @Override protected void onStop(){
        handler.removeCallbacks(apply);if(!stopping)flushChanges();
        if(service!=null)service.removeListener(statusListener);
        if(bound){unbindService(connection);bound=false;}service=null;
        super.onStop();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy();}
    public void changed(){dirty=true;handler.removeCallbacks(apply);handler.postDelayed(apply,35);view.invalidate();}
    private LinearLayout column() {
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);
        int pad=(int)(20*getResources().getDisplayMetrics().density);box.setPadding(pad,pad/2,pad,pad/2);return box;
    }
    private TextView note(String message){TextView t=new TextView(this);t.setText(message);t.setTextSize(14);t.setPadding(0,8,0,12);return t;}
    public void menu() {
        String[] options={"Подключить аудиосессию","Диагностика подключения","Пресеты","Сохранить пресет",state.limiter?"Лимитер: включён":"Лимитер: выключен","Что делает AUTO","Остановить службу","О приложении","Оформление"};
        new AlertDialog.Builder(this).setTitle("Session EQ").setItems(options,(d,which)->{
            switch(which) {
                case 0: connect();break;
                case 1: diagnostics();break;
                case 2: presets();break;
                case 3: savePreset();break;
                case 4: state.limiter=!state.limiter;changed();break;
                case 5: new AlertDialog.Builder(this).setTitle("AUTO")
                    .setMessage("AUTO уменьшает входное усиление с учётом самой усиленной полосы, чтобы оставить запас до перегрузки. Значение ползунка предусиления сохраняется. Это собственная реализация, её поведение может отличаться от AIMP.")
                    .setPositiveButton("Понятно",null).show();break;
                case 6:
                    flushChanges();stopping=true;handler.removeCallbacks(apply);
                    state.enabled=false;state.save(this);
                    if(EqService.instance!=null)EqService.instance.shutdown();
                    finish();break;
                case 7: new AlertDialog.Builder(this).setTitle("Session EQ 0.4.3")
                    .setMessage("20-полосный эквалайзер для аудиосессий других плееров.\n\nНезависимая реализация интерфейса, вдохновлённая AIMP. Значения пресетов AIMP Windows указаны отдельно. Иконки: Google Material Icons, Apache 2.0.\n\nКод приложения: Apache 2.0. Обработка — Android DynamicsProcessing; совпадение звучания с AIMP не измерено. Приложение не связано с разработчиками AIMP.\n\nПеретаскивай точки по горизонтали. Удерживай точку или нажми на число для точного ввода.\n\nРазрешение на микрофон и доступ в интернет не требуются.")
                    .setPositiveButton("Закрыть",null).show();break;
                case 8: appearance();break;
            }
        }).show();
    }
    private void appearance() {
        int selected=getSharedPreferences("appearance",0).getInt("theme",0);
        new AlertDialog.Builder(this).setTitle("Оформление")
            .setSingleChoiceItems(new String[]{"Как в системе","Светлая тема","Тёмная тема"},selected,(dialog,which)->{
                if(which!=selected) {
                    state.save(this);
                    getSharedPreferences("appearance",0).edit().putInt("theme",which).apply();
                    dialog.dismiss();recreate();
                } else dialog.dismiss();
            }).setNegativeButton("Отмена",null).show();
    }
    public void connect() {
        LinearLayout box=column();
        box.addView(note("Запусти музыку и укажи текущий ID аудиосессии. При перезапуске плеера он может измениться.\n\nДля автоматического подключения выбери Session EQ как внешний эквалайзер в плеере и возобнови воспроизведение."));
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER);input.setHint("ID аудиосессии");
        if(state.manualSession>0)input.setText(Integer.toString(state.manualSession));
        box.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Подключить сессию").setView(box)
            .setPositiveButton("Подключить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                int id=Integer.parseInt(input.getText().toString().trim());
                if(id<=0)throw new NumberFormatException();
                state.manualSession=id;state.save(this);
                queueSession(new Intent().putExtra(AudioEffect.EXTRA_AUDIO_SESSION,id)
                    .putExtra(AudioEffect.EXTRA_PACKAGE_NAME,"Вручную"));
                dialog.dismiss();handler.postDelayed(this::diagnostics,450);
            } catch(NumberFormatException error){input.setError("Введи положительный ID сессии");}
        }));dialog.show();
    }
    public void diagnostics() {
        EqService service=EqService.instance;
        String message=service==null?"Нет подключённой службы. Открой экран приложения заново.":service.diagnostics();
        ScrollView scroll=new ScrollView(this);TextView text=note(message);text.setTextIsSelectable(true);
        text.setPadding(32,16,32,16);scroll.addView(text);
        new AlertDialog.Builder(this).setTitle("Диагностика").setView(scroll).setPositiveButton("Закрыть",null)
            .setNeutralButton("Копировать",(d,w)->{
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Session EQ",message));
                Toast.makeText(this,"Диагностика скопирована",Toast.LENGTH_SHORT).show();
            }).setNegativeButton("Подключить",(d,w)->connect()).show();
    }
    public void editBand(int band) {
        EditText input=new EditText(this);input.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(Locale.US,"%.1f",state.gains[band]));input.selectAll();
        LinearLayout box=column();box.addView(note("Усиление от −15 до +15 dB"));box.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(EqMath.LABELS[band]+" Hz").setView(box)
            .setPositiveButton("Применить",null).setNegativeButton("Отмена",null)
            .setNeutralButton("0 dB",(d,w)->{state.gains[band]=0;state.preset="Пользовательский";changed();}).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try {
                float gain=Float.parseFloat(input.getText().toString().replace(',','.'));
                if(!Float.isFinite(gain)||gain < -15||gain>15)throw new NumberFormatException();
                state.gains[band]=gain;state.preset="Пользовательский";changed();dialog.dismiss();
            }catch(NumberFormatException error){input.setError("Значение от −15 до +15");}
        }));dialog.show();
    }
    public void presets() {
        ArrayList<String> names=new ArrayList<>();names.add("Rock — со скриншота");names.add("Flat — все полосы 0 dB");
        LinkedHashMap<String,float[]> windows=new LinkedHashMap<>();
        try { windows.putAll(WindowsPresets.load(this)); }
        catch(java.io.IOException|JSONException error){Toast.makeText(this,"Не удалось загрузить Windows-пресеты",Toast.LENGTH_LONG).show();}
        names.addAll(windows.keySet());
        names.addAll(new TreeSet<>(getSharedPreferences("presets",0).getAll().keySet()));
        new AlertDialog.Builder(this).setTitle("Пресеты").setItems(names.toArray(new String[0]),(d,which)->{
            if(which==0){System.arraycopy(EqMath.ROCK,0,state.gains,0,20);state.preset="Rock";}
            else if(which==1){Arrays.fill(state.gains,0);state.preset="Flat";}
            else if(which<2+windows.size()) {
                String name=names.get(which);
                System.arraycopy(windows.get(name),0,state.gains,0,20);state.preset=name;
            }
            else try {
                JSONObject preset=new JSONObject(getSharedPreferences("presets",0).getString(names.get(which),"{}"));
                JSONArray bands=preset.getJSONArray("bands");
                for(int i=0;i<20;i++)state.gains[i]=EqMath.clamp((float)bands.getDouble(i),-15,15);
                state.preamp=EqMath.clamp((float)preset.optDouble("preamp",0),-15,15);
                state.balance=EqMath.clamp((float)preset.optDouble("balance",0),-1,1);
                state.auto=preset.optBoolean("auto",false);state.limiter=preset.optBoolean("limiter",true);
                state.preset=names.get(which);
            }catch(JSONException error){Toast.makeText(this,"Не удалось прочитать пресет",Toast.LENGTH_LONG).show();}
            changed();
        }).setNegativeButton("Закрыть",null).show();
    }
    private void savePreset() {
        EditText name=new EditText(this);name.setSingleLine(true);name.setHint("Название пресета");
        LinearLayout box=column();box.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Сохранить пресет").setView(box)
            .setPositiveButton("Сохранить",null).setNegativeButton("Отмена",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String title=name.getText().toString().trim();
            if(title.isEmpty()){name.setError("Введи название");return;}
            if(title.equals("Rock — со скриншота")||title.equals("Flat — все полосы 0 dB")||title.startsWith(WindowsPresets.PREFIX)){name.setError("Выбери другое название");return;}
            try {
                JSONObject preset=new JSONObject();JSONArray bands=new JSONArray();for(float gain:state.gains)bands.put(gain);
                preset.put("bands",bands).put("preamp",state.preamp).put("balance",state.balance).put("auto",state.auto).put("limiter",state.limiter);
                getSharedPreferences("presets",0).edit().putString(title,preset.toString()).apply();state.preset=title;changed();dialog.dismiss();
            }catch(JSONException error){name.setError(error.getMessage());}
        }));dialog.show();
    }
}
