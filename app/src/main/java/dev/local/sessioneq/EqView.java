package dev.local.sessioneq;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.os.Handler;
import android.view.*;
import java.util.Locale;

public final class EqView extends View {
    private final MainActivity host;
    private final EqState state;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler=new Handler();
    private final Path path=new Path();
    private final int BG,TEXT,MUTED,ORANGE,TRACK,BORDER,ZERO,TOOLBAR,BUTTON_ON,BUTTON_OFF,ON_TEXT,OFF_TEXT;
    private final Drawable tuneIcon,settingsIcon;
    private float scale=1,h,graphBottom,rowStep,downX,downY;
    private int lane=-1,band;
    private boolean dragged,longPressed;
    private String status="Ожидание сессии";
    private final Runnable longPress;
    public EqView(MainActivity context,EqState state) {
        super(context); host=context; this.state=state; setFocusable(true);
        BG=context.getColor(R.color.eq_background);TEXT=context.getColor(R.color.eq_text);
        MUTED=context.getColor(R.color.eq_muted);ORANGE=context.getColor(R.color.eq_accent);
        TRACK=context.getColor(R.color.eq_track);BORDER=context.getColor(R.color.eq_border);
        ZERO=context.getColor(R.color.eq_zero);TOOLBAR=context.getColor(R.color.eq_toolbar);
        BUTTON_ON=context.getColor(R.color.eq_button_on);BUTTON_OFF=context.getColor(R.color.eq_button_off);
        ON_TEXT=context.getColor(R.color.eq_on_text);OFF_TEXT=context.getColor(R.color.eq_off_text);
        tuneIcon=context.getDrawable(R.drawable.ic_tune);settingsIcon=context.getDrawable(R.drawable.ic_settings);
        longPress=()->{if(lane==2&&!dragged){longPressed=true; host.editBand(band);}};
        setContentDescription("Эквалайзер: 20 полос. Настройки справа внизу, пресеты слева от них. Для точной настройки полосы удерживай точку на графике.");
    }
    public void setStatus(String text) { if(!status.equals(text)){status=text;invalidate();} }
    private void text(Canvas c,String text,float x,float y,float size,int color,Paint.Align align,boolean bold) {
        p.setStyle(Paint.Style.FILL);p.setColor(color);p.setTextSize(size);p.setTextAlign(align);
        p.setTypeface(bold?Typeface.create("sans-serif",Typeface.BOLD):Typeface.create("sans-serif",Typeface.NORMAL));
        c.drawText(text,x,y,p);
    }
    private void line(Canvas c,float x1,float y1,float x2,float y2,int color,float width) {
        p.setStyle(Paint.Style.STROKE);p.setColor(color);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);c.drawLine(x1,y1,x2,y2,p);p.setStyle(Paint.Style.FILL);
    }
    private void circle(Canvas c,float x,float y,float radius,int color) {p.setColor(color);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,radius,p);}
    private String db(float v) {return String.format(Locale.US,"%.1f dB",v);}
    private void slider(Canvas c,float y,float value) {
        line(c,67,y,293,y,TRACK,7);
        circle(c,67+(value+1)*113,y,9.5f,ORANGE);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.drawColor(BG);
        scale=getWidth()/360f; h=getHeight()/scale;
        graphBottom=h-76; rowStep=(graphBottom-183)/20f;
        canvas.save();canvas.scale(scale,scale);
        text(canvas,"Эквалайзер",66,24,16,TEXT,Paint.Align.LEFT,true);
        p.setTextSize(12);p.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        String subtitle=TextUtils.ellipsize(state.preset,new android.text.TextPaint(p),246,TextUtils.TruncateAt.END).toString();
        text(canvas,subtitle,66,43,12,TEXT,Paint.Align.LEFT,false);
        line(canvas,20,27,39,27,TEXT,2.5f);line(canvas,20,27,30,17,TEXT,2.5f);line(canvas,20,27,30,37,TEXT,2.5f);
        boolean attached=status.startsWith("Сессия ")&&status.contains(" · ");
        circle(canvas,338,23,3.5f,attached?ORANGE:MUTED);
        text(canvas,"Баланс",180,66,10,TEXT,Paint.Align.CENTER,false);
        text(canvas,"L",44,85,10,MUTED,Paint.Align.CENTER,false);
        text(canvas,"R",316,85,10,MUTED,Paint.Align.CENTER,false);
        slider(canvas,82,state.balance);
        text(canvas,Math.round(state.balance*100)+"%",180,103,10,MUTED,Paint.Align.CENTER,false);
        text(canvas,"Предусиление",180,119,10,TEXT,Paint.Align.CENTER,false);
        text(canvas,"-15 dB",20,139,10,MUTED,Paint.Align.LEFT,false);
        text(canvas,"+15 dB",313,139,10,MUTED,Paint.Align.LEFT,false);
        slider(canvas,136,state.preamp/15);
        text(canvas,db(state.preamp),180,159,10,MUTED,Paint.Align.CENTER,false);
        p.setColor(BORDER);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.5f);
        canvas.drawRect(53,167,307,graphBottom,p);p.setStyle(Paint.Style.FILL);
        for(float y=168;y<graphBottom;y+=8) line(canvas,180,y,180,Math.min(y+2,graphBottom),ZERO,1.2f);
        path.reset();
        for(int i=0;i<20;i++) {
            float y=176+rowStep*(i+.5f),x=180+state.gains[i]*254/30f;
            text(canvas,EqMath.LABELS[i],41,y+3.5f,10,MUTED,Paint.Align.RIGHT,false);
            text(canvas,String.format(Locale.US,"%+.1f",state.gains[i]),320,y+3.5f,10,MUTED,Paint.Align.LEFT,false);
            if(i==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        p.setColor(ORANGE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.3f);canvas.drawPath(path,p);p.setStyle(Paint.Style.FILL);
        for(int i=0;i<20;i++) circle(canvas,180+state.gains[i]*254/30f,176+rowStep*(i+.5f),3.4f,ORANGE);
        p.setColor(TOOLBAR); canvas.drawRect(0,h-69,360,h,p);
        pill(canvas,11,h-55,74,h-23,state.enabled?BUTTON_ON:BUTTON_OFF);
        pill(canvas,92,h-55,155,h-23,state.auto?BUTTON_ON:BUTTON_OFF);
        text(canvas,state.enabled?"ON":"OFF",43,h-35,12,state.enabled?ON_TEXT:OFF_TEXT,Paint.Align.CENTER,true);
        text(canvas,"AUTO",124,h-35,12,state.auto?ON_TEXT:OFF_TEXT,Paint.Align.CENTER,true);
        tuneIcon.setBounds(258,(int)(h-51),282,(int)(h-27));tuneIcon.draw(canvas);
        settingsIcon.setBounds(312,(int)(h-51),336,(int)(h-27));settingsIcon.draw(canvas);
        canvas.restore();
    }
    private void pill(Canvas c,float l,float t,float r,float b,int color) {p.setColor(color);c.drawRoundRect(l,t,r,b,18,18,p);}
    private void change(float x) {
        if(lane==0) state.balance=Math.round(EqMath.clamp((x-180)/113,-1,1)*100)/100f;
        else if(lane==1) state.preamp=Math.round(EqMath.clamp((x-180)/113*15,-15,15)*10)/10f;
        else if(lane==2){state.gains[band]=Math.round(EqMath.clamp((x-180)*30/254,-15,15)*10)/10f;state.preset="Пользовательский";}
        host.changed();invalidate();
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event) {
        float x=event.getX()/scale,y=event.getY()/scale;
        switch(event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX=x;downY=y;dragged=false;longPressed=false;lane=-1;
                if(y>=71&&y<=104) lane=0;
                else if(y>=125&&y<=155) lane=1;
                else if(y>=167&&y<=graphBottom) {lane=2;band=Math.max(0,Math.min(19,(int)((y-176)/rowStep)));handler.postDelayed(longPress,550);}
                getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(Math.abs(x-downX)>3||Math.abs(y-downY)>3){dragged=true;handler.removeCallbacks(longPress);}
                if(dragged&&!longPressed&&lane>=0)change(x);return true;
            case MotionEvent.ACTION_UP:
                handler.removeCallbacks(longPress);
                if(longPressed){lane=-1;return true;}
                if(lane==2&&(downX<53||downX>307)){host.editBand(band);}
                else if(lane>=0) change(x);
                else if(!dragged) {
                    if(y<50&&x<55)host.finish();
                    else if(y<50&&x>310)host.diagnostics();
                    else if(y<50)host.presets();
                    else if(y>h-69) {
                        if(x<82){state.enabled=!state.enabled;host.changed();}
                        else if(x<165){state.auto=!state.auto;host.changed();}
                        else if(x<295)host.presets();
                        else host.menu();
                    }
                }
                lane=-1;performClick();invalidate();return true;
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPress);lane=-1;return true;
        }
        return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(longPress);super.onDetachedFromWindow();}
}
