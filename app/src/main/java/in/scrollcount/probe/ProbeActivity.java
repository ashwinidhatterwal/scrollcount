package in.scrollcount.probe;
import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
/** Native product UI. Only visible screens refresh; SQLite reads are revision-cached. */
public final class ProbeActivity extends Activity {
    private final Handler handler=new Handler();
    private HistoryStore history;
    private FriendsPage friendsPage;
    private android.content.SharedPreferences prefs;
    private Ui ui;
    private LinearLayout root,body,nav,onboardingCard;
    private ScrollView page;
    private int tab;
    private TextView dateLabel,watched,total,time,skips,instagram,youtube,facebook,snapchat,average,longer,longest,lifetime,lifetimeDetail,status,detail,error,goalText,weekTotal,weekDetail;
    private Button toggle;
    private LinearLayout countingState;
    private ProgressBar igBar,ytBar,fbBar,snapBar,goalBar;
    private TrendView trend;
    private LinearLayout sessions;
    private HistoryStore.Stats stats=new HistoryStore.Stats();
    private long loadedRevision=-1,loadedDay=-1;
    private boolean loading,resumed;
    private int pendingReminder;
    private final Runnable refresh=new Runnable() {
        @Override public void run() {
            if(!resumed)return;
            if(tab!=0 && tab!=2)return;
            draw();
            long day=HistoryStore.midnight(System.currentTimeMillis());
            if(!loading && (history.revision.get()!=loadedRevision || day!=loadedDay)) {
                loading=true;
                history.load(result-> {
                    loading=false;
                    stats=result;
                    loadedRevision=result.revision;
                    loadedDay=day;
                    if(!isDestroyed()) {
                        draw();
                        if(tab==2)drawSessions();
                    }
                });
            }
            handler.postDelayed(this,1000);
        }
    };
    @Override public void onCreate(Bundle state) {
        prefs=getSharedPreferences("privacy",0);
        Detectors.load(getSharedPreferences("supported_apps",0).getAll());
        boolean dark=prefs.getBoolean("dark",false);
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);
        ServiceHealth.attach(this);
        history=HistoryStore.get(this);
        ui=new Ui(this,dark);
        if(state!=null) {
            tab=state.getInt("tab",0);
            pendingReminder=state.getInt("pendingReminder",0);
        }
        boolean firstOpen=!prefs.contains("firstOpen");
        if(firstOpen) {
            android.content.SharedPreferences.Editor edit=prefs.edit().putBoolean("firstOpen",true);
            if(!prefs.contains("running"))edit.putBoolean("running",true);
            if(!prefs.contains("liveBadge"))edit.putBoolean("liveBadge",true);
            edit.apply();
        }
        readVersions();
        ui.window();
        root=ui.column();
        root.setBackgroundColor(ui.paper);
        setContentView(root);
        if(Build.VERSION.SDK_INT>=30)root.setOnApplyWindowInsetsListener((v,insets)-> {
            android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars());
            root.setPadding(b.left,b.top,b.right,b.bottom);
            return insets;
        });
        page=new ScrollView(this);
        page.setFillViewport(true);
        page.setClipToPadding(false);
        root.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        // Center the content on tablets, with generous space on phones.
        FrameLayout frame=new FrameLayout(this);
        body=ui.column();
        int width=getResources().getConfiguration().screenWidthDp;
        FrameLayout.LayoutParams content=new FrameLayout.LayoutParams(width>720?ui.dp(680):-1,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        body.setPadding(ui.dp(22),ui.dp(24),ui.dp(22),ui.dp(24));
        HourlyFriendJob.ensure(this);
        friendsPage=new FriendsPage(this,ui,body);
        frame.addView(body,content);
        page.addView(frame);
        nav=ui.row();
        nav.setBackgroundColor(ui.surface);
        nav.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(10));
        root.addView(nav);
        showTab(tab);
        boolean invited=handleInvitation(getIntent());
        if(!invited && prefs.getInt("consentVersion",0)<3)onboarding();
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);handleInvitation(intent);
    }
    private boolean handleInvitation(Intent intent){
        if(intent==null || intent.getData()==null)return false;
        String link=intent.getData().toString();intent.setData(null);
        try{FriendInvitation.parse(link);}catch(Exception e){Toast.makeText(this,"Invalid invitation",Toast.LENGTH_SHORT).show();return false;}
        showTab(1);friendsPage.openInvitation(link);return true;
    }
    private void showTab(int selected) {
        if(friendsPage!=null)friendsPage.stop();
        tab=Math.max(0,Math.min(3,selected));
        body.removeAllViews();
        nav.removeAllViews();
        total=null;
        trend=null;
        sessions=null;
        String[] labels= {
            "Today","Friends","Insights","Settings"
        };
        for(int i=0;i<4;i++) {
            final int index=i;
            LinearLayout item=ui.column();
            item.setGravity(Gravity.CENTER);
            item.setPadding(ui.dp(6),ui.dp(8),ui.dp(6),ui.dp(8));
            item.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),ui.shape(i==tab?ui.soft:ui.surface,18),null));
            ImageView icon=new ImageView(this);
            icon.setImageResource(getResources().getIdentifier(new String[]{"ic_today","ic_friends","ic_insights","ic_settings"}[i],"drawable",getPackageName()));
            icon.setColorFilter(i==tab?ui.accent:ui.muted);
            item.addView(icon,new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));
            ui.gap(item,5);
            TextView label=ui.text(labels[i],12,i==tab?ui.accent:ui.muted,i==tab);
            label.setGravity(Gravity.CENTER);label.setSingleLine(true);label.setPadding(0,0,0,0);
            item.addView(label,new LinearLayout.LayoutParams(-1,Math.max(ui.dp(22),Math.round(17*getResources().getDisplayMetrics().scaledDensity))));
            item.setSelected(i==tab);
            item.setContentDescription(labels[i]+(i==tab?", selected":""));
            item.setFocusable(true);
            item.setOnClickListener(v->showTab(index));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
            p.setMargins(ui.dp(3),0,ui.dp(3),0);
            nav.addView(item,p);
        }
        page.scrollTo(0,0);
        if(tab==0)today();
        else if(tab==1)friendsPage.render();
        else if(tab==2)insights();
        else settingsPage();
        draw();
        if(tab==1 && resumed)friendsPage.resume();
        handler.removeCallbacks(refresh);if(resumed && (tab==0 || tab==2))handler.post(refresh);
    }
    private void title(String title,String subtitle) {
        TextView heading=ui.text(title,30,ui.ink,true);
        Ui.markHeading(heading);
        body.addView(heading);
        dateLabel=ui.text(subtitle,14,ui.muted,false);
        body.addView(dateLabel);
        ui.gap(body,22);
    }
    private void addCard(LinearLayout card) {
        body.addView(card);
        ui.gap(body,16);
    }
    private void today() {
        title("ScrollCount",new SimpleDateFormat("EEEE, d MMMM",Locale.getDefault()).format(new Date()));
        onboardingCard=ui.card();
        onboardingCard.addView(ui.text("Start your private counter",18,ui.ink,true));
        onboardingCard.addView(ui.text("One setup step, then it works automatically.",13,ui.muted,false));
        ui.gap(onboardingCard,12);
        onboardingCard.addView(ui.button("Set up counting",true,this::onboarding));
        addCard(onboardingCard);
        LinearLayout state=ui.card();
        countingState=state;
        LinearLayout controls=ui.row(),words=ui.column();
        status=ui.text("Set up automatic counting",15,ui.ink,true);
        detail=ui.text("",12,ui.muted,false);
        words.addView(status);
        words.addView(detail);
        controls.addView(words,new LinearLayout.LayoutParams(0,-2,1));

        toggle=ui.button("Pause counting",false,()-> {
            prefs.edit().putBoolean("running",!prefs.getBoolean("running",false)).apply();
            draw();
        });
        LinearLayout.LayoutParams action=new LinearLayout.LayoutParams(-2,-2);
        action.setMargins(ui.dp(12),0,0,0);
        controls.addView(toggle,action);
        state.addView(controls);
        addCard(state);
        LinearLayout hero=ui.column();
        hero.setPadding(ui.dp(24),ui.dp(26),ui.dp(24),ui.dp(24));
        android.graphics.drawable.GradientDrawable glow=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff244f3b,0xff102e22});
        glow.setCornerRadius(ui.dp(28));
        hero.setBackground(glow);
        hero.addView(ui.text("TODAY’S TOTAL",11,0xffc0dfc8,true));
        ui.gap(hero,10);
        total=ui.text("0",72,0xfff5fff6,true);
        total.setSingleLine(true);
        total.setAutoSizeTextTypeUniformWithConfiguration(32,72,2,android.util.TypedValue.COMPLEX_UNIT_SP);
        hero.addView(total,new LinearLayout.LayoutParams(-1,ui.dp(100)));
        hero.addView(ui.text("Reels & Shorts",22,0xfff5fff6,true));
        ui.gap(hero,12);
        
        addCard(hero);
        LinearLayout platforms=ui.card();
        ui.heading(platforms,"By App",null);
        instagram=platformRow(platforms,"Instagram",0xffac4b77);
        igBar=bar(platforms,0xffac4b77);
        ui.gap(platforms,14);
        youtube=platformRow(platforms,"YouTube",0xffbd5650);
        ytBar=bar(platforms,0xffbd5650);
        ui.gap(platforms,14);
        facebook=platformRow(platforms,"Facebook",0xff477abe);
        fbBar=bar(platforms,0xff477abe);
        ui.gap(platforms,14);
        snapchat=platformRow(platforms,"Snapchat",0xffb88a16);
        snapBar=bar(platforms,0xffb88a16);
        addCard(platforms);
        LinearLayout tiles=ui.row();
        boolean stacked=getResources().getConfiguration().fontScale>1.2f || getResources().getConfiguration().screenWidthDp<350;
        if(stacked)tiles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout first=ui.card(),second=ui.card();
        first.addView(ui.text("Feed time",13,ui.muted,false));
        time=ui.text("0s",28,ui.ink,true);
        first.addView(time);
        
        second.addView(ui.text("Quick skips",13,ui.muted,false));
        skips=ui.text("0",28,ui.ink,true);
        second.addView(skips);
        
        if(stacked) {
            tiles.addView(first,new LinearLayout.LayoutParams(-1,-2));
            ui.gap(tiles,12);
            tiles.addView(second,new LinearLayout.LayoutParams(-1,-2));
        }
        else {
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);
            p.setMargins(0,0,ui.dp(6),0);
            tiles.addView(first,p);
            p=new LinearLayout.LayoutParams(0,-2,1);
            p.setMargins(ui.dp(6),0,0,0);
            tiles.addView(second,p);
        }
        body.addView(tiles);
        ui.gap(body,16);
        LinearLayout trendCard=ui.card();
        ui.heading(trendCard,"Last 7 days",null);
        trend=new TrendView(ui);
        trend.onDay(this::dayDetails);
        trendCard.addView(trend,new LinearLayout.LayoutParams(-1,ui.dp(185)));
        addCard(trendCard);
        LinearLayout facts=ui.card();
        ui.heading(facts,"Viewing details",null);
        watched=ui.metric(facts,"Watched","0");
        average=ui.metric(facts,"Average per item","0s");
        longer=ui.metric(facts,"Views over 10s","0");
        longest=ui.metric(facts,"Longest session","0s");
        addCard(facts);
        LinearLayout goal=ui.card();
        ui.heading(goal,"Daily goal",null);
        goalText=ui.text("Set a goal in Settings",14,ui.muted,false);
        goal.addView(goalText);
        goalBar=bar(goal,ui.accent);
        goal.addView(ui.button("Adjust goal",false,()->showTab(3)));
        addCard(goal);
        LinearLayout all=ui.card();
        all.addView(ui.text("YOUR HISTORY",11,ui.muted,true));
        lifetime=ui.text("0 seen",28,ui.ink,true);
        all.addView(lifetime);
        lifetimeDetail=ui.text("History begins when you start counting.",13,ui.muted,false);
        all.addView(lifetimeDetail);
        addCard(all);
        footer();
    }
    private TextView platformRow(LinearLayout parent,String name,int color) {
        if(getResources().getConfiguration().fontScale>1.3f) {
            parent.addView(ui.text(name,14,ui.ink,true));
            TextView value=ui.text("0",22,ui.ink,true);
            parent.addView(value);
            return value;
        }
        LinearLayout row=ui.row();
        View dot=new View(this);
        dot.setBackground(ui.shape(color,5));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ui.dp(8),ui.dp(8));
        p.setMargins(0,0,ui.dp(10),0);
        row.addView(dot,p);
        row.addView(ui.text(name,14,ui.ink,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView value=ui.text("0",20,ui.ink,true);
        row.addView(value);
        parent.addView(row);
        return value;
    }
    private ProgressBar bar(LinearLayout parent,int color) {
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setMax(1000);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(color));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(ui.soft));
        parent.addView(bar,new LinearLayout.LayoutParams(-1,ui.dp(8)));
        return bar;
    }
    private void insights() {
        title("Insights","Last 7 days");
        LinearLayout week=ui.card();
        week.addView(ui.text("LAST 7 DAYS",11,ui.muted,true));
        weekTotal=ui.text("0 seen",38,ui.ink,true);
        week.addView(weekTotal);
        weekDetail=ui.text("",14,ui.muted,false);
        week.addView(weekDetail);
        ui.gap(week,12);
        trend=new TrendView(ui);
        trend.onDay(this::dayDetails);
        week.addView(trend,new LinearLayout.LayoutParams(-1,ui.dp(190)));
        addCard(week);
        ui.heading(body,"Sessions",null);
        sessions=ui.column();
        body.addView(sessions);
        drawSessions();
        footer();
    }
    private void drawSessions() {
        if(sessions==null)return;
        sessions.removeAllViews();
        if(stats.recent.isEmpty()) {
            LinearLayout empty=ui.card();
            empty.addView(ui.text("Your history starts here",19,ui.ink,true));
            empty.addView(ui.text("Use Reels or Shorts after setup. Your sessions will appear automatically.",14,ui.muted,false));
            sessions.addView(empty);
            return;
        }
        for(HistoryStore.Session session:stats.recent) {
            LinearLayout card=ui.card();
            LinearLayout row=ui.row();
            row.addView(ui.text(session.platform,16,ui.ink,true),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(ui.text(Ui.duration(session.duration),16,ui.accent,true));
            card.addView(row);
            card.addView(ui.text(date(session.start,"EEE d MMM · h:mm a"),12,ui.muted,false));
            card.addView(ui.text(Ui.number(session.seen)+" seen  ·  "+Ui.number(session.skips)+" skips"+(session.open?"  ·  In progress":""),13,ui.muted,false));
            card.setMinimumHeight(ui.dp(84));
            card.setFocusable(true);
            card.setOnClickListener(v->message(session.platform+" session",date(session.start,"EEE d MMM, h:mm a")+"\n\nActive feed time: "+Ui.duration(session.duration)+"\nSeen: "+Ui.number(session.seen)+"\nQuick skips: "+Ui.number(session.skips)+"\nLonger views: "+Ui.number(session.longer)+(session.open?"\n\nThe current item appears after it ends.":"\n\nTime outside the feed is excluded.")));
            sessions.addView(card);
            ui.gap(sessions,12);
        }
    }
    private void modifiedApps() {
        String[] names={"Instagram", "YouTube", "Instagram Lite", "Facebook", "Snapchat"};
        String[] packages={InstagramSignals.PACKAGE,YouTubeSignals.PACKAGE,"com.instagram.lite","com.facebook.katana","com.snapchat.android"};
        new AlertDialog.Builder(this).setTitle("Original app").setItems(names,(dialog,which)-> {
            Intent launch=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
            java.util.TreeMap<String,String> apps=new java.util.TreeMap<>();
            for(android.content.pm.ResolveInfo item:getPackageManager().queryIntentActivities(launch,0)) {
                String pkg=item.activityInfo.packageName;
                if(!pkg.equals(getPackageName()))apps.put(item.loadLabel(getPackageManager())+" · "+pkg,pkg);
            }
            String[] labels=apps.keySet().toArray(new String[0]);
            if(labels.length==0) { message("Modified apps","No installed apps available."); return; }
            new AlertDialog.Builder(this).setTitle("Choose modified app").setItems(labels,(d,index)-> {
                String pkg=apps.get(labels[index]);
                new AlertDialog.Builder(this).setTitle("Enable counting?").setMessage("ScrollCount will inspect this app's visible short-video player using the "+names[which]+" detection profile. Only choose a modified version of that app. Changed layouts may need diagnostics.")
                    .setPositiveButton("Enable",(confirm,button)-> {
                        getSharedPreferences("supported_apps",0).edit().putString(pkg,packages[which]).apply();
                        Detectors.load(getSharedPreferences("supported_apps",0).getAll()); ProbeService.reloadApps();
                    }).setNegativeButton("Cancel",null).show();
            }).setNegativeButton("Cancel",null).show();
        }).setNeutralButton("Remove",(dialog,which)-> {
            android.content.SharedPreferences saved=getSharedPreferences("supported_apps",0);
            String[] packagesSaved=saved.getAll().keySet().toArray(new String[0]);
            new AlertDialog.Builder(this).setTitle("Remove modified app").setItems(packagesSaved,(d,index)-> {
                saved.edit().remove(packagesSaved[index]).apply();
                Detectors.load(getSharedPreferences("supported_apps",0).getAll()); ProbeService.reloadApps();
            }).setNegativeButton("Cancel",null).show();
        }).setNegativeButton("Cancel",null).show();
    }
    private void settingsPage() {
        title("Settings","Preferences");
        LinearLayout counting=ui.card();
        ui.heading(counting,"Counting",null);
        switchRow(counting,"Automatic counting","Requires Accessibility",prefs.getBoolean("running",false),enabled-> {
            if(enabled && (prefs.getInt("consentVersion",0)<3 || !ProbeStore.connected)) {
                showTab(3);
                onboarding();
                return;
            }
            prefs.edit().putBoolean("running",enabled).apply();
        });
        counting.addView(ui.text(ProbeStore.connected?"Accessibility service connected":"Accessibility service needs enabling",12,ui.muted,false));
        counting.addView(ui.button("Accessibility settings",false,()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        counting.addView(ui.button("Background settings",false,()-> {
            try { startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)); }
            catch(RuntimeException missing) { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()))); }
        }));
        counting.addView(ui.button("Modified apps",false,this::modifiedApps));
        addCard(counting);
        LinearLayout appearance=ui.card();
        ui.heading(appearance,"Appearance",null);
        switchRow(appearance,"Dark theme","",ui.dark,value-> {
            prefs.edit().putBoolean("dark",value).apply();
            recreate();
        });
        switchRow(appearance,"Live badge","Combined daily Scrolls over Reels and Shorts",prefs.getBoolean("liveBadge",false),value-> {
            prefs.edit().putBoolean("liveBadge",value).apply();
        });
        addCard(appearance);
        LinearLayout wellbeing=ui.card();
        ui.heading(wellbeing,"Reminders",null);
        int goal=prefs.getInt("dailyGoal",0);
        wellbeing.addView(ui.button(goal>0?"Daily goal · "+goal+" seen":"Set daily goal",false,this::chooseGoal));
        int reminder=prefs.getInt("reminderMinutes",0);
        ui.gap(wellbeing,8);
        wellbeing.addView(ui.button(reminder>0?"Session reminder · "+reminder+" min":"Session reminder · Off",false,this::chooseReminder));
        wellbeing.addView(ui.text("One reminder per session",12,ui.muted,false));
        NotificationManager manager=getSystemService(NotificationManager.class);
        NotificationChannel channel=manager.getNotificationChannel("breaks");
        if(reminder>0 && (!manager.areNotificationsEnabled() || (channel!=null && channel.getImportance()==NotificationManager.IMPORTANCE_NONE))) {
            wellbeing.addView(ui.text("Android notifications are disabled. Enable them to receive your reminder.",13,ui.muted,false));
            wellbeing.addView(ui.button("Open notification settings",false,()-> {
                Intent settings=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                settings.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
                startActivity(settings);
            }));
        }
        addCard(wellbeing);
        LinearLayout privacy=ui.card();
        ui.heading(privacy,"Privacy & data",null);
        privacy.addView(ui.text("History stays on this phone",14,ui.muted,false));
        ui.gap(privacy,12);
        privacy.addView(ui.button("Privacy policy",false,()->message("Privacy policy",Privacy.TEXT+ReleaseInfo.summary())));
        ui.gap(privacy,8);
        privacy.addView(ui.button("Privacy policy website",false,()->openPolicyPage("/privacy")));
        ui.gap(privacy,8);
        privacy.addView(ui.button("Delete sharing profile",false,()->openPolicyPage("/delete")));
        ui.gap(privacy,8);
        privacy.addView(ui.button("Export history",false,this::export));
        ui.gap(privacy,8);
        Button delete=ui.button("Delete all history",false,this::deleteHistory);
        delete.setTextColor(ui.dark?0xffffb6ac:0xffa13c30);
        privacy.addView(delete);
        addCard(privacy);
        LinearLayout help=ui.card();
        ui.heading(help,"Help & compatibility",null);
        help.addView(ui.button("How counting works",false,()->message("Understanding your counts",Privacy.COUNTING)));
        ui.gap(help,8);
        help.addView(ui.button("If counting stops",false,()->message("Keep counting available","Check that ScrollCount's Accessibility service is enabled and counting is on. Reopen the app after a reboot if your phone requires it. Many phones (vivo, Xiaomi, Oppo, Realme, Samsung) switch Accessibility services off to save battery: set ScrollCount's battery use to Unrestricted, allow Auto-start, and lock the app in the recent-apps list. On Android 13+ with a manually installed APK, also allow Restricted settings in the app's info page.\n\nApp updates can change accessible layouts. PiP and small split-screen players are not counted. Ultra-fast Shorts or identical labels may be missed. Buffering time in a confirmed feed is estimated exposure, not playback time.")));
        ui.gap(help,8);
        help.addView(ui.button("Compatibility & diagnostics",false,()->message("Compatibility",ProbeStore.appVersions+"\n\n"+ProbeStore.signals+"\n\nThese versions have been observed, not certified. Open diagnostics for a local test report.")));
        ui.gap(help,8);
        switchRow(help,"Local diagnostics","For troubleshooting",prefs.getBoolean("diagnostics",false),value-> {
            prefs.edit().putBoolean("diagnostics",value).apply();
            ProbeStore.setDiagnostics(value,(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        });
        help.addView(ui.button("Open diagnostics",false,()->startActivity(new Intent(this,DiagnosticsActivity.class))));
        addCard(help);
        footer();
    }
    private void openPolicyPage(String path) {
        String server=FriendStore.get(this).server();
        if(!FriendApi.validServer(server))server=FriendInvitation.DEFAULT_SERVER;
        String url=server+path;
        try { startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url))); }
        catch(android.content.ActivityNotFoundException e) { message("Open in a browser",url); }
    }
    private void switchRow(LinearLayout parent,String title,String subtitle,boolean checked,java.util.function.Consumer<Boolean> action) {
        LinearLayout row=ui.row();
        LinearLayout words=ui.column();
        words.addView(ui.text(title,15,ui.ink,true));
        if(!subtitle.isEmpty())words.addView(ui.text(subtitle,12,ui.muted,false));
        row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);
        toggle.setContentDescription(title);
        toggle.setChecked(checked);
        toggle.setMinHeight(ui.dp(48));
        toggle.setPadding(ui.dp(14),0,0,0);
        toggle.setThumbTintList(android.content.res.ColorStateList.valueOf(ui.accent));
        row.addView(toggle);
        toggle.setOnCheckedChangeListener((button,value)->action.accept(value));
        parent.addView(row);
        ui.gap(parent,12);
    }
    private void footer() {
        ui.gap(body,10);
        body.addView(ui.text("Private counting. Optional sharing. Just awareness.",12,ui.muted,false));
        body.addView(ui.text("ScrollCount 1.2.12",11,ui.muted,false));
        error=ui.text("",13,ui.dark?0xffffb6ac:0xffa13c30,false);
        body.addView(error);
    }
    private void draw() {
        if(ui==null)return;
        CountingEngine.Live live=ProbeService.live();
        long day=HistoryStore.midnight(System.currentTimeMillis());
        boolean current=live!=null && live.startedAt>=day;
        long plusSeen=current && live.duration>=2000?1:0;
        long seen=stats.seen+plusSeen,duration=stats.duration+(current?live.duration:0),all=stats.lifetime+(live!=null && live.duration>=2000?1:0);
        if(total!=null) {
            dateLabel.setText(date(System.currentTimeMillis(),"EEEE, d MMMM"));
            long detected=stats.instagramItems+stats.youtubeItems+stats.facebookItems+stats.snapchatItems;
            total.setText(loadedRevision<0?"—":Ui.number(detected));
            watched.setText(Ui.number(seen));
            time.setText(Ui.duration(duration));
            skips.setText(Ui.number(stats.skips));
            instagram.setText(Ui.number(stats.instagramItems));
            youtube.setText(Ui.number(stats.youtubeItems));
            facebook.setText(Ui.number(stats.facebookItems));
            snapchat.setText(Ui.number(stats.snapchatItems));
            fbBar.setProgress(detected==0?0:(int)(stats.facebookItems*1000/detected));
            snapBar.setProgress(detected==0?0:(int)(stats.snapchatItems*1000/detected));
            igBar.setProgress(detected==0?0:(int)(stats.instagramItems*1000/detected));
            ytBar.setProgress(detected==0?0:(int)(stats.youtubeItems*1000/detected));
            long items=stats.seen+stats.skips+(current?1:0);
            average.setText(Ui.duration(items==0?0:duration/items));
            longer.setText(Ui.number(stats.longer+(current && live.duration>=10000?1:0)));
            longest.setText(Ui.duration(Math.max(stats.longest,live!=null && live.sessionStartedAt>=day?live.sessionDuration:0)));
            lifetime.setText(Ui.number(all)+" seen");
            long first=stats.first>0?stats.first:live!=null?live.startedAt:0;
            lifetimeDetail.setText(first==0?"History begins when you start counting.":Ui.duration(stats.lifetimeDuration+(live!=null?live.duration:0))+" in feeds · Since "+date(first,"d MMM yyyy"));
            boolean ready=prefs.getInt("consentVersion",0)>=3 && ProbeStore.connected,running=prefs.getBoolean("running",false);
            onboardingCard.setVisibility(ready?View.GONE:View.VISIBLE);
            countingState.setVisibility(ready?View.VISIBLE:View.GONE);
            toggle.setVisibility(ready?View.VISIBLE:View.GONE);
            status.setText(!ready?"Set up automatic counting":running?"Counting is on":"Counting is paused");
            detail.setText(!ready?"Check Accessibility settings: access is off or the service is disconnected.":running?"Works while this app is closed.":"Saved history stays safe.");
            toggle.setText(running?"Pause":"Resume");
            int goal=prefs.getInt("dailyGoal",0);
            goalText.setText(goal==0?"Set a goal in Settings":seen<goal?Ui.number(seen)+" of "+Ui.number(goal)+" seen today · "+Ui.number(goal-seen)+" to your chosen limit":"You've reached your chosen limit of "+Ui.number(goal)+" seen. Pause whenever you like.");
            goalBar.setVisibility(goal==0?View.GONE:View.VISIBLE);
            goalBar.setProgress(goal==0?0:(int)Math.min(1000,seen*1000/goal));
        }
        if(trend!=null)trend.update(stats.days,plusSeen);
        if(tab==2 && weekTotal!=null) {
            long week=plusSeen,weekTime=current?live.duration:0,weekSkips=0;
            for(HistoryStore.Day d:stats.days) {
                week+=d.seen;
                weekTime+=d.duration;
                weekSkips+=d.skips;
            }
            weekTotal.setText(Ui.number(week)+" seen");
            long baseline=0,days=0;
            for(int i=0;i<6;i++) {
                if(stats.first>0 && stats.days[i].start>=HistoryStore.midnight(stats.first)) {
                    baseline+=stats.days[i].seen;
                    days++;
                }
            }
            String usual=days>0?"\nRecent daily average: "+Ui.number(Math.round((double)baseline/days))+" seen":"";
            weekDetail.setText(Ui.duration(weekTime)+" in feeds · "+Ui.number(weekSkips)+" quick skips\n"+(stats.first>0 && stats.first<stats.days[0].start?"Previous 7 days: "+Ui.number(stats.previousSeen)+" seen":"Your personal baseline will grow with your history.")+usual);
        }
        if(error!=null) {
            error.setText(history.error);
            error.setVisibility(history.error.isEmpty()?View.GONE:View.VISIBLE);
        }
    }
    private void dayDetails(int index) {
        if(index<0) {
            String[] days=new String[7];
            for(int i=0;i<7;i++)days[i]=date(stats.days[i].start,"EEE d MMM");
            new AlertDialog.Builder(this).setTitle("Choose a day").setItems(days,(d,i)->dayDetails(i)).show();
            return;
        }
        HistoryStore.Day d=stats.days[index];
        CountingEngine.Live live=ProbeService.live();
        boolean today=index==6 && live!=null && live.startedAt>=d.start;
        long seen=d.seen+(today && live.duration>=2000?1:0),duration=d.duration+(today?live.duration:0);
        message(date(d.start,"EEEE, d MMMM"),"Seen: "+Ui.number(seen)+"\nInstagram: "+Ui.number(d.instagram+(today && live.duration>=2000 && live.platform.equals("Instagram")?1:0))+"\nYouTube: "+Ui.number(d.youtube+(today && live.duration>=2000 && live.platform.equals("YouTube")?1:0))+"\nFacebook: "+Ui.number(d.facebook+(today && live.duration>=2000 && live.platform.equals("Facebook")?1:0))+"\nSnapchat: "+Ui.number(d.snapchat+(today && live.duration>=2000 && live.platform.equals("Snapchat")?1:0))+"\n\nFeed time: "+Ui.duration(duration)+"\nQuick skips: "+Ui.number(d.skips)+"\nLonger views: "+Ui.number(d.longer+(today && live.duration>=10000?1:0)));
    }
    private void onboarding() {
        startActivity(new Intent(this,OnboardingActivity.class));
    }
    private void chooseGoal() {
        EditText input=new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(prefs.getInt("dailyGoal",0)>0?prefs.getInt("dailyGoal",150):150));
        input.setSelectAllOnFocus(true);
        LinearLayout wrap=ui.column();
        wrap.setPadding(ui.dp(24),0,ui.dp(24),0);
        wrap.addView(ui.text("A gentle intention, never a block. Choose 1–10,000 seen per day.",14,ui.muted,false));
        wrap.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Daily seen goal").setView(wrap).setNeutralButton("Turn off",(d,w)-> {
            prefs.edit().putInt("dailyGoal",0).apply();
            showTab(3);
        }).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v-> {
            try {
                int value=Integer.parseInt(input.getText().toString());
                if(value<1 || value>10000)throw new NumberFormatException();
                prefs.edit().putInt("dailyGoal",value).apply();
                dialog.dismiss();
                showTab(3);
            }
            catch(NumberFormatException e) {
                input.setError("Choose a number from 1 to 10,000");
            }
        }));
        dialog.show();
    }
    private void chooseReminder() {
        new AlertDialog.Builder(this).setTitle("Gentle session reminder").setItems(new String[] {
            "Off","After 15 minutes","After 30 minutes","After 45 minutes","After 60 minutes"
        },(d,index)-> {
            int value=index*15;
            if(value>0 && Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
                pendingReminder=value;
                requestPermissions(new String[] {
                    android.Manifest.permission.POST_NOTIFICATIONS
                },7);
            }
            else {
                prefs.edit().putInt("reminderMinutes",value).apply();
                showTab(3);
            }
        }).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==7) {
            prefs.edit().putInt("reminderMinutes",results.length>0 && results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED?pendingReminder:0).apply();
            showTab(3);
        }
    }
    private void export() {
        Intent save=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        save.addCategory(Intent.CATEGORY_OPENABLE);
        save.setType("text/csv");
        save.putExtra(Intent.EXTRA_TITLE,"ScrollCount-"+date(System.currentTimeMillis(),"yyyy-MM-dd")+".csv");
        startActivityForResult(save,8);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==8 && result==RESULT_OK && data!=null && data.getData()!=null)history.export(getApplicationContext(),data.getData(),success-> {
            if(!isDestroyed())Toast.makeText(this,success?"Saved your completed viewing history":"Could not save. Choose a different location.",Toast.LENGTH_LONG).show();
        });
    }
    private void deleteHistory() {
        new AlertDialog.Builder(this).setTitle("Delete all history?").setMessage("All saved counts and viewing history will be cleared. Your counting preference stays the same, so automatic counting can continue from zero.").setNegativeButton("Keep history",null).setPositiveButton("Delete history",(d,w)-> {
            ProbeService.stopForReset();
            getSystemService(NotificationManager.class).cancel(1);
            history.reset(success-> {
                ProbeService.finishReset();
                if(isDestroyed())return;
                if(!success) {
                    message("Could not clear history",history.error);
                    draw();
                    return;
                }
                stats=new HistoryStore.Stats();
                loadedRevision=-1;
                showTab(tab);
                Toast.makeText(this,prefs.getBoolean("running",false)?"History cleared · Counting stays on":"History cleared",Toast.LENGTH_SHORT).show();
            });
        }).show();
    }
    private void message(String title,String text) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton("Got it",null).show();
    }
    private static String date(long at,String pattern) {
        return new SimpleDateFormat(pattern,Locale.getDefault()).format(new Date(at));
    }
    private void readVersions() {
        StringBuilder versions=new StringBuilder();
        for(String p:new String[] {
            getPackageName(),InstagramSignals.PACKAGE,YouTubeSignals.PACKAGE,"com.instagram.lite","com.facebook.katana","com.facebook.lite","com.snapchat.android"
        }) {
            try {
                android.content.pm.PackageInfo i=getPackageManager().getPackageInfo(p,0);
                versions.append(p).append(' ').append(i.versionName).append(" ("+(Build.VERSION.SDK_INT>=28?i.getLongVersionCode():i.versionCode)+")\n");
            }
            catch(android.content.pm.PackageManager.NameNotFoundException e) {
                versions.append(p).append(" not installed\n");
            }
        }
        ProbeStore.appVersions=versions.toString();
        ProbeStore.setDiagnostics(prefs.getBoolean("diagnostics",false),(getApplicationInfo().flags&android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab",tab);
        state.putInt("pendingReminder",pendingReminder);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if(friendsPage!=null)friendsPage.destroy();
        super.onDestroy();
    }
    @Override public void onResume() {
        super.onResume();
        if(tab==3) {
            int position=page.getScrollY();
            showTab(3);
            page.post(()->page.scrollTo(0,position));
        }
        resumed=true;
        if(tab==1)friendsPage.resume();
        handler.removeCallbacks(refresh);
        handler.post(refresh);
    }
    @Override public void onPause() {
        if(friendsPage!=null)friendsPage.stop();
        resumed=false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }
}
