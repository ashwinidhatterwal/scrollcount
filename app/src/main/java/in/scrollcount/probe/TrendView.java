package in.scrollcount.probe;
import android.graphics.*;
import android.view.View;
import android.view.MotionEvent;
import java.text.SimpleDateFormat;
import java.util.Locale;
/** Seven-day bar chart, including a text alternative and day-detail interaction. */
final class TrendView extends View {
    private final Ui ui;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private HistoryStore.Day[] days;
    private long liveSeen;
    private final long[] drawnStarts=new long[7],drawnValues=new long[7];
    private boolean initialized;
    private java.util.function.IntConsumer selected;
    TrendView(Ui ui) {
        super(ui.activity);
        this.ui=ui;
        setFocusable(true);
        setContentDescription("Seven-day trend. Double tap to choose a day.");
        setOnClickListener(v-> {
            if(selected!=null)selected.accept(-1);
        });
    }
    void update(HistoryStore.Day[] days,long liveSeen) {
        boolean changed=!initialized;
        for(int i=0;i<7;i++){
            long value=days[i].seen+(i==6?liveSeen:0);
            if(drawnStarts[i]!=days[i].start || drawnValues[i]!=value)changed=true;
            drawnStarts[i]=days[i].start;drawnValues[i]=value;
        }
        this.days=days;
        this.liveSeen=liveSeen;
        if(!changed)return;initialized=true;
        StringBuilder description=new StringBuilder("Short videos seen in the last seven days. ");
        for(int i=0;i<7;i++)description.append(new SimpleDateFormat("EEE d MMM",Locale.getDefault()).format(new java.util.Date(days[i].start))).append(": ").append(days[i].seen+(i==6?liveSeen:0)).append(". ");
        setContentDescription(description+"Double tap for day details.");
        invalidate();
    }
    void onDay(java.util.function.IntConsumer action) {
        selected=action;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        if(days==null)return;
        float w=getWidth(),h=getHeight(),bottom=h-ui.dp(30),top=ui.dp(27),step=w/7;
        long max=1;
        for(int i=0;i<7;i++)max=Math.max(max,days[i].seen+(i==6?liveSeen:0));
        paint.setColor(ui.line);
        paint.setStrokeWidth(ui.dp(1));
        c.drawLine(ui.dp(4),bottom,w-ui.dp(4),bottom,paint);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif-medium",0));
        paint.setTextSize(11*getResources().getDisplayMetrics().scaledDensity);
        for(int i=0;i<7;i++) {
            long n=days[i].seen+(i==6?liveSeen:0);
            float x=step*(i+.5f),height=n==0?ui.dp(3):Math.max(ui.dp(5),(bottom-top)*n/max),bw=Math.min(ui.dp(32),step*.5f);
            paint.setColor(i==6?ui.accent:ui.soft);
            c.drawRoundRect(x-bw/2,bottom-height,x+bw/2,bottom,ui.dp(6),ui.dp(6),paint);
            paint.setColor(ui.ink);
            String label=n>=1000000?String.format(Locale.getDefault(),"%.1fm",n/1000000f):n>=1000?String.format(Locale.getDefault(),"%.1fk",n/1000f):Ui.number(n);
            float safeX=Math.max(paint.measureText(label)/2+ui.dp(2),Math.min(w-paint.measureText(label)/2-ui.dp(2),x));
            if(getResources().getConfiguration().fontScale<=1.2f || i==6)c.drawText(label,safeX,Math.max(ui.dp(16),bottom-height-ui.dp(8)),paint);
            paint.setColor(i==6?ui.accent:ui.muted);
            String day=i==6?"Today":new SimpleDateFormat("EEE",Locale.getDefault()).format(new java.util.Date(days[i].start));
            if(getResources().getConfiguration().fontScale>1.2f)day=day.substring(0,1);
            c.drawText(day,x,h-ui.dp(8),paint);
        }
    }
    @Override public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()==MotionEvent.ACTION_UP) {
            if(selected!=null)selected.accept(Math.min(6,Math.max(0,(int)(e.getX()/(getWidth()/7f)))));
            return true;
        }
        return super.onTouchEvent(e);
    }
}
