package in.scrollcount.probe;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
public final class DiagnosticsActivity extends Activity {
    private final Handler handler=new Handler();
    private TextView output;
    private final Runnable refresh=new Runnable() {
        public void run() {
            output.setText(ProbeStore.report());
            handler.postDelayed(this,2000);
        }
    };
    @Override public void onCreate(Bundle state) {
        boolean dark=getSharedPreferences("privacy",0).getBoolean("dark",false);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        Ui ui=new Ui(this,dark);
        ui.window();
        ScrollView page=new ScrollView(this);
        page.setBackgroundColor(ui.paper);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        int p=Math.round(18*getResources().getDisplayMetrics().density);
        body.setPadding(p,p,p,p);
        page.addView(body);
        setContentView(page);
        if(android.os.Build.VERSION.SDK_INT>=30) body.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets b=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(p,p+b.top,p,p+b.bottom);
            return insets;
        });
        Button back=new Button(this);
        back.setText("Back to ScrollCount");
        back.setOnClickListener(v->finish());
        body.addView(back);
        TextView note=new TextView(this);
        note.setTextColor(ui.muted);
        note.setText("Enable Local diagnostics in Settings before a test. Reports stay in memory; release builds never retain tree dumps. Diagnostics are temporary and sanitized. Video changes here differ from 'videos seen': the home screen and Scrolls badge count all confirmed items, including quick skips; Seen applies a 2-second threshold.");
        body.addView(note);
        Button share=new Button(this);
        share.setText("Share sanitized report");
        share.setOnClickListener(v-> {
            Intent i=new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT,ProbeStore.report());
            startActivity(Intent.createChooser(i,"Share diagnostics"));
        });
        body.addView(share);
        output=new TextView(this);
        output.setTextSize(13);
        output.setTextColor(ui.ink);
        output.setTextIsSelectable(true);
        body.addView(output);
    }
    @Override public void onResume() {
        super.onResume();
        handler.post(refresh);
    }
    @Override public void onPause() {
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
