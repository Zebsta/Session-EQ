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
    private Map<Integer,String> pendingSelection;
    private int scanGeneration;
    private boolean scanRunning;
    private String lastScan="Поиск ещё не выполнялся";
    private AlertDialog scanDialog;
    private final ArrayDeque<Intent> pendingSessions=new ArrayDeque<>();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable apply=this::flushChanges;
    private final EqService.Listener statusListener=this::serviceChanged;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder) {
            service=((EqService.LocalBinder)binder).getService();
            flushChanges();service.applySettings();service.addListener(statusListener);
            while(!pendingSessions.isEmpty())service.session(pendingSessions.removeFirst());
            if(pendingSelection!=null){
                boolean connected=service.connectSelected(pendingSelection);pendingSelection=null;
                if(!connected)connectionFailed();
            }
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
            String owner=source.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME);
            if(owner==null)owner=getCallingPackage();
            if(owner==null&&getReferrer()!=null&&"android-app".equals(getReferrer().getScheme()))owner=getReferrer().getHost();
            if(!SessionPolicy.mayAttach(owner))return;
            queueSession(new Intent(source).putExtra(AudioEffect.EXTRA_PACKAGE_NAME,owner));
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
        scanGeneration++;if(scanDialog!=null){scanDialog.dismiss();scanDialog=null;}
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
                case 7: new AlertDialog.Builder(this).setTitle("Session EQ 0.6.0")
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
        if(!PlayerDirectory.hasAccess(this)){
            if(!state.autoPlayers.isEmpty())showPlayers(new ArrayList<>());else discoveryAccess();
            return;
        }
        scanPlayers(null);
    }
    private void discoveryAccess() {
        final String command="adb shell pm grant dev.local.sessioneq android.permission.DUMP";
        new AlertDialog.Builder(this).setTitle("Настройка поиска плееров")
            .setMessage("Для поиска уже играющих плееров Android требует разовый доступ к диагностике аудио. На компьютере с USB-отладкой выполни:\n\n"+command+"\n\nЗатем нажми «Подключить аудиосессию» ещё раз. Поиск выполняется только по нажатию. Доступ к уведомлениям и микрофону не нужен.")
            .setPositiveButton("Понятно",null).setNegativeButton("Ввести ID вручную",(d,w)->connectManual()).setNeutralButton("Скопировать команду",(d,w)->{
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Session EQ",command));
            }).show();
    }
    private void scanPlayers(PlayerDirectory.Player selected) {scanPlayers(selected,false);}
    private void scanPlayers(PlayerDirectory.Player selected,boolean additive) {
        if(scanRunning){Toast.makeText(this,"Поиск плееров ещё выполняется",Toast.LENGTH_SHORT).show();return;}
        scanRunning=true;
        final int request=++scanGeneration;
        LinearLayout box=column();ProgressBar spinner=new ProgressBar(this);box.addView(spinner);
        box.addView(note(selected==null?"Поиск проигрывателей…":"Проверка выбранного проигрывателя…"));
        AlertDialog progress=new AlertDialog.Builder(this).setTitle("Подключить аудиосессию").setView(box)
            .setNegativeButton("Отмена",(d,w)->scanGeneration++).create();
        progress.setOnCancelListener(d->scanGeneration++);scanDialog=progress;progress.show();
        android.content.Context app=getApplicationContext();
        Thread worker=new Thread(()->{
            List<PlayerDirectory.Player> found=null;Exception error=null;
            try {found=PlayerDirectory.query(app);}catch(Exception failure){error=failure;}
            final List<PlayerDirectory.Player> result=found;final Exception failure=error;
            runOnUiThread(()->{
                scanRunning=false;
                if(request!=scanGeneration||isFinishing()||isDestroyed())return;
                progress.dismiss();scanDialog=null;
                if(failure instanceof SecurityException&&!PlayerDirectory.hasAccess(MainActivity.this)){discoveryAccess();return;}
                if(failure!=null){
                    lastScan=failure.getClass().getSimpleName()+": "+failure.getMessage();
                    android.util.Log.w("SessionEQ", "Player discovery failed", failure);
                    new AlertDialog.Builder(this).setTitle("Поиск плееров")
                    .setMessage(failure.getMessage()==null?"Не удалось прочитать список проигрывателей.":failure.getMessage())
                    .setPositiveButton("Закрыть",null).setNeutralButton("Повторить",(d,w)->connect())
                    .setNegativeButton(state.autoPlayers.isEmpty()?null:"Выбранные",(d,w)->showPlayers(new ArrayList<>())).show();return;}
                lastScan="Поиск завершён: плееров "+result.size();
                if(selected==null){showPlayers(result);return;}
                for(PlayerDirectory.Player current:result)
                    if(current.uid==selected.uid&&current.packageName.equals(selected.packageName)){
                        if(additive) {
                            if(service!=null&&state.enabled&&state.autoPlayers.contains(current.packageName))
                                for(int id:current.sessionIds)service.attach(id,current.packageName);
                        } else attachPlayer(current);
                        return;
                    }
                new AlertDialog.Builder(this).setTitle("Воспроизведение остановлено")
                    .setMessage("Запусти музыку в выбранном плеере и обнови список.")
                    .setPositiveButton("Обновить",(d,w)->connect()).setNegativeButton("Закрыть",null).show();
            });
        },"session-eq-player-scan");worker.setDaemon(true);worker.start();
    }
    private void showPlayers(List<PlayerDirectory.Player> found) {
        List<PlayerDirectory.Player> players=new ArrayList<>(found);
        for(String owner:new TreeSet<>(state.autoPlayers)) {
            boolean present=false;for(PlayerDirectory.Player player:players)if(player.packageName.equals(owner))present=true;
            if(present||!SessionPolicy.mayAttach(owner))continue;
            try {
                android.content.pm.ApplicationInfo info=getPackageManager().getApplicationInfo(owner,0);
                players.add(new PlayerDirectory.Player(owner,getPackageManager().getApplicationLabel(info).toString(),info.uid));
            } catch(PackageManager.NameNotFoundException missing) {
                players.add(new PlayerDirectory.Player(owner,owner+" (не установлено)",-1));
            }
        }
        if(players.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Нет играющих плееров")
                .setMessage("Запусти музыку и обнови список. AIMP исключён: у него есть собственный эквалайзер.")
                .setPositiveButton("Обновить",(d,w)->connect()).setNegativeButton("Закрыть",null).show();return;
        }
        ArrayAdapter<PlayerDirectory.Player> adapter=new ArrayAdapter<PlayerDirectory.Player>(this,android.R.layout.simple_list_item_2,players){
            @Override public View getView(int position,View reusable,ViewGroup parent) {
                PlayerDirectory.Player player=getItem(position);
                LinearLayout row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
                int pad=(int)(16*getResources().getDisplayMetrics().density);row.setPadding(pad,pad/2,pad,pad/2);
                ImageView icon=new ImageView(MainActivity.this);row.addView(icon,new LinearLayout.LayoutParams(pad*3,pad*3));
                try {icon.setImageDrawable(getPackageManager().getApplicationIcon(player.packageName));}
                catch(PackageManager.NameNotFoundException ignored){}
                LinearLayout words=new LinearLayout(MainActivity.this);words.setOrientation(LinearLayout.VERTICAL);words.setPadding(pad,0,0,0);
                TextView title=new TextView(MainActivity.this);title.setText(player.label);title.setTextSize(17);words.addView(title);
                TextView detail=new TextView(MainActivity.this);detail.setText(player.sessionIds.isEmpty()?"Сейчас не воспроизводит звук":player.sessionIds.size()>1?"Воспроизводится · несколько потоков":"Сейчас воспроизводит звук");
                detail.setTextSize(13);words.addView(detail);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
                CheckBox automatic=new CheckBox(MainActivity.this);automatic.setText("Авто");automatic.setFocusable(false);
                automatic.setContentDescription("Автоподключение: "+player.label);
                automatic.setChecked(state.autoPlayers.contains(player.packageName));
                automatic.setOnCheckedChangeListener((button,checked)->{
                    handler.removeCallbacks(apply);
                    if(checked&&player.uid<0){button.setChecked(false);return;}
                    if(checked){state.autoPlayers.add(player.packageName);state.enabled=true;}
                    else state.autoPlayers.remove(player.packageName);
                    state.save(MainActivity.this);dirty=false;
                    if(service!=null) {
                        service.applySettings();
                        if(checked&&!player.sessionIds.isEmpty())scanPlayers(player,true);
                    }
                    view.invalidate();
                });
                // A removed package can be unchecked but cannot be newly armed.
                if(player.uid<0&&!automatic.isChecked())automatic.setEnabled(false);
                row.addView(automatic);return row;
            }
        };
        ListView list=new ListView(this);list.setAdapter(adapter);
        LinearLayout box=column();box.addView(note("Нажми на плеер для подключения сейчас. «Авто» запоминает приложение и подключает его новые сессии. При ON остаётся тихое уведомление ожидания. AIMP исключён."));
        box.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog chooser=new AlertDialog.Builder(this).setTitle("Подключение плееров").setView(box)
            .setNeutralButton("Обновить",(d,w)->{
                if(PlayerDirectory.hasAccess(this))scanPlayers(null);else discoveryAccess();
            }).setNegativeButton("Закрыть",null).create();
        list.setOnItemClickListener((parent,row,which,id)->{
            PlayerDirectory.Player player=players.get(which);
            if(player.sessionIds.isEmpty()) {Toast.makeText(this,"Запусти музыку в этом плеере и обнови список",Toast.LENGTH_SHORT).show();return;}
            chooser.dismiss();scanPlayers(player);
        });
        chooser.setOnShowListener(d->{
            android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
            chooser.getWindow().setLayout(-1,Math.min((int)(560*metrics.density),(int)(metrics.heightPixels*.8f)));
        });chooser.show();
    }
    private void attachPlayer(PlayerDirectory.Player player) {
        if(!SessionPolicy.mayAttach(player.packageName))return;
        handler.removeCallbacks(apply);state.enabled=true;state.save(this);dirty=false;
        if(service!=null) {
            if(!service.connectSelected(player.sessions())){connectionFailed();view.invalidate();return;}
            Toast.makeText(this,"Подключено: "+player.label,Toast.LENGTH_SHORT).show();
        } else pendingSelection=player.sessions();
        view.invalidate();
    }
    private void connectionFailed() {
        new AlertDialog.Builder(this).setTitle("Не удалось подключить эквалайзер")
            .setMessage(service==null?"Открой экран приложения ещё раз.":service.status())
            .setPositiveButton("Закрыть",null).setNeutralButton("Диагностика",(d,w)->diagnostics()).show();
    }
    private void connectManual() {
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
        String message="Доступ DUMP: "+(PlayerDirectory.hasAccess(this)?"выдан":"не выдан")+"\n"+lastScan+"\n\n"+(service==null?"Нет подключённой службы. Открой экран приложения заново.":service.diagnostics());
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
