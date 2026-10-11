package in.scrollcount.probe;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
/** Two distinct screens: product introduction, then affirmative Accessibility disclosure. */
public final class OnboardingActivity extends Activity {
    private final android.os.Handler handler=new android.os.Handler();
    private int readinessChecks;
    private final Runnable ready=new Runnable() {
        public void run() {
            if(getSharedPreferences("privacy",0).getInt("consentVersion",0)>=2 && ProbeStore.connected)finish();
            else if(getSharedPreferences("privacy",0).getInt("consentVersion",0)>=2 && ++readinessChecks<10)handler.postDelayed(this,1000);
        }
    };
    private Ui ui;
    private LinearLayout root,content,actions;
    private boolean privacy;
    @Override public void onCreate(Bundle saved) {
        boolean dark=getSharedPreferences("privacy",0).getBoolean("dark",false);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(saved);
        ui=new Ui(this,dark);
        ui.window();
        privacy=saved!=null && saved.getBoolean("privacy");
        root=ui.column();
        root.setBackgroundColor(ui.paper);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(b.left,b.top,b.right,b.bottom);
            return insets;
        });
        ScrollView scroll=new ScrollView(this);
        content=ui.column();
        content.setPadding(ui.dp(26),ui.dp(32),ui.dp(26),ui.dp(24));
        scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        actions=ui.column();
        actions.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),ui.dp(20));
        root.addView(actions);
        render();
    }
    private void render() {
        content.removeAllViews();
        actions.removeAllViews();
        content.addView(ui.text(privacy?"02 / YOUR PERMISSION":"01 / WELCOME",11,ui.accent,true));
        ui.gap(content,24);
        if(!privacy) {
            ImageView icon=new ImageView(this);
            icon.setImageResource(getResources().getIdentifier("scroll_counter_brand","drawable",getPackageName()));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(76),ui.dp(76));
            p.setMargins(0,0,0,ui.dp(28));
            content.addView(icon,p);
            content.addView(ui.text("Know how much\nyou scroll.",36,ui.ink,true));
            content.addView(ui.text("A private counter for Instagram Reels and YouTube Shorts.",17,ui.muted,false));
            ui.gap(content,28);
            feature("A clearer picture","See counts, feed time and the patterns across your week.");
            feature("Automatic after setup","Use your apps normally. ScrollCount works in the background.");
            feature("Private counting","History stays on this phone. Friends sharing is optional.");
            actions.addView(ui.button("Continue",true,()-> {
                privacy=true;
                render();
            }));
            ui.gap(actions,8);
            actions.addView(ui.button("Maybe later",false,this::finish));
        }
        else {
            content.addView(ui.text("Why Accessibility\nis needed",32,ui.ink,true));
            ui.gap(content,18);
            LinearLayout disclosure=ui.card();
            disclosure.addView(ui.text(Privacy.DISCLOSURE,15,ui.ink,false));
            content.addView(disclosure);
            ui.gap(content,16);
            content.addView(ui.text("You stay in control. Counting can be paused at any time.",13,ui.muted,false));
            actions.addView(ui.button("Agree and open settings",true,()-> {
                getSharedPreferences("privacy",0).edit().putInt("consentVersion",2).putBoolean("running",true).apply();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            }));
            ui.gap(actions,8);
            actions.addView(ui.button("Back",false,()-> {
                privacy=false;
                render();
            }));
        }
    }
    private void feature(String title,String description) {
        LinearLayout card=ui.card();
        card.addView(ui.text(title,16,ui.ink,true));
        card.addView(ui.text(description,13,ui.muted,false));
        content.addView(card);
        ui.gap(content,12);
    }
    @Override public void onResume() {
        super.onResume();
        readinessChecks=0;
        handler.post(ready);
    }
    @Override public void onPause() {
        handler.removeCallbacks(ready);
        super.onPause();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("privacy",privacy);
        super.onSaveInstanceState(state);
    }
}
