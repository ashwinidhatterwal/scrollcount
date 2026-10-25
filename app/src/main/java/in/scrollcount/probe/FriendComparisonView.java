package in.scrollcount.probe;
import android.graphics.*;
import android.view.View;
/** Friendly, labelled comparison bars, including an accessible text equivalent. */
final class FriendComparisonView extends View {
    private float progress=1;private android.animation.ValueAnimator animator;
    private final Ui ui;private final long yours,theirs;private final String friend;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    FriendComparisonView(Ui ui,long yours,long theirs,String friend){super(ui.activity);this.ui=ui;this.yours=yours;this.theirs=theirs;this.friend=friend;setContentDescription("You: "+Ui.number(yours)+" scrolls. "+friend+": "+Ui.number(theirs)+" scrolls.");setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();if(android.animation.ValueAnimator.areAnimatorsEnabled()){progress=0;animator=android.animation.ValueAnimator.ofFloat(0,1);animator.setDuration(650);animator.setInterpolator(new android.view.animation.DecelerateInterpolator());animator.addUpdateListener(a->{progress=(float)a.getAnimatedValue();invalidate();});animator.start();}}
    @Override protected void onDetachedFromWindow(){if(animator!=null)animator.cancel();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float scale=getResources().getDisplayMetrics().scaledDensity;long max=Math.max(1,Math.max(yours,theirs));
        for(int i=0;i<2;i++){float top=ui.dp(10)+13*scale+i*(getHeight()/2f);long value=i==0?yours:theirs;paint.setTextSize(13*scale);paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));paint.setColor(ui.muted);String name=i==0?"You":friend;String amount=Ui.number(value);float available=getWidth()-paint.measureText(amount)-ui.dp(16);while(name.length()>1&&paint.measureText(name)>available)name=name.substring(0,name.length()-1);c.drawText(name,0,top,paint);c.drawText(amount,getWidth()-paint.measureText(amount),top,paint);
            paint.setColor(ui.soft);c.drawRoundRect(0,top+ui.dp(10),getWidth(),top+ui.dp(26),ui.dp(8),ui.dp(8),paint);paint.setColor(i==0?ui.accent:(ui.dark?0xffbaa9ee:0xff8270b5));float width=getWidth()*((float)value/max)*progress;if(width>0)c.drawRoundRect(0,top+ui.dp(10),Math.max(ui.dp(5),width),top+ui.dp(26),ui.dp(8),ui.dp(8),paint);
        }
    }
}
