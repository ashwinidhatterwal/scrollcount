package in.scrollcount.probe;
import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;
/** A small native design system: readable type, touch targets and shared spacing. */
final class Ui {
    final Activity activity;
    final boolean dark;
    final int ink,muted,paper,surface,accent,soft,line;
    Ui(Activity a,boolean dark) {
        activity=a;
        this.dark=dark;
        ink=dark?0xffeef5f0:0xff182d26;
        muted=dark?0xffafbeb5:0xff64756c;
        paper=dark?0xff101a15:0xfff5f7f3;
        surface=dark?0xff1c2a22:0xffffffff;
        accent=dark?0xffa6ddbd:0xff206b4b;
        soft=dark?0xff293e32:0xffe8f1e8;
        line=dark?0xff35483c:0xffe3e9e1;
    }
    void window() {
        android.view.Window w=activity.getWindow();
        w.setStatusBarColor(paper);
        w.setNavigationBarColor(surface);
        int flags=w.getDecorView().getSystemUiVisibility();
        int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        w.getDecorView().setSystemUiVisibility(dark?flags&~light:flags|light);
    }
    int dp(float n) {
        return Math.round(n*activity.getResources().getDisplayMetrics().density);
    }
    TextView text(String value,int size,int color,boolean bold) {
        TextView t=new TextView(activity);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setFontFeatureSettings("tnum");
        t.setIncludeFontPadding(false);
        t.setLineSpacing(dp(3),1);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        t.setPadding(0,dp(3),0,dp(3));
        return t;
    }
    LinearLayout column() {
        LinearLayout v=new LinearLayout(activity);
        v.setOrientation(1);
        return v;
    }
    LinearLayout row() {
        LinearLayout v=new LinearLayout(activity);
        v.setOrientation(0);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }
    GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }
    LinearLayout card() {
        LinearLayout c=column();
        c.setPadding(dp(20),dp(20),dp(20),dp(20));
        GradientDrawable d=shape(surface,24);
        d.setStroke(dp(1),line);
        c.setBackground(d);
        c.setElevation(dp(1));
        return c;
    }
    void gap(LinearLayout parent,int h) {
        parent.addView(new View(activity),new LinearLayout.LayoutParams(1,dp(h)));
    }
    Button button(String label,boolean primary,Runnable action) {
        Button b=new Button(activity);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.create("sans-serif-medium",0));
        b.setAllCaps(false);
        b.setTextColor(primary?(dark?0xff142d20:0xffffffff):accent);
        b.setPadding(dp(18),dp(12),dp(18),dp(12));
        b.setMinHeight(dp(48));
        b.setMinWidth(0);
        b.setBackground(new RippleDrawable(ColorStateList.valueOf(dark?0x22ffffff:0x22000000),shape(primary?accent:soft,14),null));
        b.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        b.setOnClickListener(v->action.run());
        return b;
    }
    TextView metric(LinearLayout parent,String label,String value) {
        boolean large=activity.getResources().getConfiguration().fontScale>1.3f;
        LinearLayout row=large?column():row();
        row.setPadding(0,dp(9),0,dp(9));
        parent.addView(row);
        TextView name=text(label,14,muted,false);
        if(large)row.addView(name);
        else row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView result=text(value,18,ink,true);
        if(!large) {
            result.setGravity(Gravity.END);
            result.setPadding(dp(12),0,0,0);
        }
        row.addView(result);
        return result;
    }
    static void markHeading(View view) {
        if(android.os.Build.VERSION.SDK_INT>=28)view.setAccessibilityHeading(true);
    }
    void heading(LinearLayout parent,String title,String subtitle) {
        TextView heading=text(title,22,ink,true);
        Ui.markHeading(heading);
        parent.addView(heading);
        if(subtitle!=null)parent.addView(text(subtitle,13,muted,false));
        gap(parent,12);
    }
    static String number(long n) {
        return String.format(Locale.getDefault(),"%,d",n);
    }
    static String duration(long ms) {
        long s=Math.max(0,ms)/1000;
        if(s>=3600)return s/3600+"h "+s%3600/60+"m";
        if(s>=60)return s/60+"m "+s%60+"s";
        return s+"s";
    }
}
