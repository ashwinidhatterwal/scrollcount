package in.scrollcount.probe;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.*;
public final class DiagnosticsActivity extends Activity {
    private final Handler handler=new Handler();
    private TextView output;
    private static final int SAVE_REPORT=42;
    private final Runnable refresh=new Runnable() {
        public void run() {
            output.setText(ProbeStore.report()+"\n\n"+ServiceHealth.report(DiagnosticsActivity.this));
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
        note.setText("Local diagnostics are on by default; you can turn them off in Settings. Service-health events remain locally after restart; label diagnostics stay in memory; release builds never retain tree dumps. Diagnostics are temporary and sanitized. Video changes here differ from 'videos seen': the home screen and Scrolls badge count all confirmed items, including quick skips; Seen applies a 2-second threshold.");
        body.addView(note);
        body.addView(ui.button("Save diagnostic file",true,()-> {
            Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                    .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,fileName());
            try{startActivityForResult(save,SAVE_REPORT);}catch(android.content.ActivityNotFoundException e){toast("No file picker available. Use Share diagnostic file.");}
        }));
        body.addView(ui.button("Share diagnostic file",false,this::shareFile));
        Button clear=new Button(this);
        clear.setText("Clear service health log");
        clear.setOnClickListener(v->ServiceHealth.clear(this));
        body.addView(clear);
        output=new TextView(this);
        output.setTextSize(13);
        output.setTextColor(ui.ink);
        output.setTextIsSelectable(true);
        body.addView(output);
    }
    private String report(){return ProbeStore.report()+"\n\n"+ServiceHealth.report(this);}
    private String fileName(){return "ScrollCount-diagnostics-"+System.currentTimeMillis()+"-"+java.util.UUID.randomUUID()+".txt";}
    private void toast(String text){if(!isDestroyed())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void shareFile(){
        final String text=report(),name=fileName();
        new Thread(()-> {
            try{
                java.io.File file=DiagnosticFiles.resolve(getCacheDir(),name);
                java.io.File directory=file.getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Unavailable cache");
                // Retain recent attachments so a receiving app can read after the chooser closes.
                java.io.File[] old=directory.listFiles();if(old!=null)for(java.io.File f:old)if(f.lastModified()<System.currentTimeMillis()-7L*24*60*60*1000)f.delete();
                try(java.io.OutputStream out=new java.io.FileOutputStream(file)){out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
                runOnUiThread(()-> {
                    if(isDestroyed())return;
                    android.net.Uri uri=new android.net.Uri.Builder().scheme("content").authority(getPackageName()+".diagnostics").appendPath(name).build();
                    Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    share.setClipData(android.content.ClipData.newRawUri("ScrollCount diagnostics",uri));
                    try{startActivity(Intent.createChooser(share,"Share diagnostic file"));}catch(android.content.ActivityNotFoundException e){toast("No sharing app available. Use Save diagnostic file.");}
                });
            }catch(java.io.IOException|RuntimeException e){runOnUiThread(()->toast("Could not create diagnostic file. Try Save diagnostic file."));}
        },"diagnostic-export").start();
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=SAVE_REPORT||result!=RESULT_OK||data==null||data.getData()==null)return;
        final android.net.Uri uri=data.getData();final String text=report();
        new Thread(()-> {
            try(java.io.OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                if(out==null)throw new java.io.IOException("No destination");out.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                runOnUiThread(()->toast("Diagnostic file saved"));
            }catch(java.io.IOException|RuntimeException e){runOnUiThread(()->toast("Could not save diagnostic file. Please try again."));}
        },"diagnostic-save").start();
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
