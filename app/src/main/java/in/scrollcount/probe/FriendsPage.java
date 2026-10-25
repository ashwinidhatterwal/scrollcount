package in.scrollcount.probe;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;
/** Friends UI observes the shared foreground/background sync owner. Counting is independent. */
final class FriendsPage {
    private final Activity activity;private final Ui ui;private final LinearLayout body;
    private final FriendStore store;private final FriendApi api=new FriendApi();private final Handler handler=new Handler();
    private final FriendSync sync;private LinearLayout cards;private TextView connection;
    private boolean visible,publishing;
    private final Runnable countObserver=()->{if(visible)publish();};private long generation,lastPublishRevision=-1,lastPublishDay=-1;
    private Dialog comparison;private String compared="",renderKey="";
    private long myReels,myShorts;private String myDay="";
    private final Runnable loop=new Runnable(){public void run(){if(!visible)return;publish();renderCards();if(comparison!=null&&comparison.isShowing()){FriendStore.Friend f=store.friends.get(compared);if(f!=null)fillComparison(f);}handler.postDelayed(this,60000);}};
    FriendsPage(Activity a,Ui u,LinearLayout b){activity=a;ui=u;body=b;store=FriendStore.get(a);sync=FriendSync.get(a);}
    void render(){
        LinearLayout header=ui.row();TextView title=ui.text("Friends",30,ui.ink,true);Ui.markHeading(title);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        ImageButton menu=new ImageButton(activity);menu.setImageResource(activity.getResources().getIdentifier("ic_more","drawable",activity.getPackageName()));menu.setColorFilter(ui.ink);menu.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22000000),ui.shape(ui.soft,16),null));menu.setContentDescription("Sharing settings");header.addView(menu,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));menu.setOnClickListener(v->sharingMenu(menu));body.addView(header);ui.gap(body,20);
        body.addView(ui.button("＋  Add friend",true,this::add));ui.gap(body,18);
        connection=ui.text("",12,ui.muted,false);body.addView(connection);ui.gap(body,12);cards=ui.column();renderKey="";body.addView(cards);renderCards();
    }
    private void sharingMenu(View anchor){
        PopupMenu menu=new PopupMenu(activity,anchor,Gravity.END);
        if(store.id().isEmpty())menu.getMenu().add("Add friend").setOnMenuItemClickListener(item->{add();return true;});
        else{
            menu.getMenu().add(store.enabled()?"Pause sharing":"Resume sharing").setOnMenuItemClickListener(item->{store.prefs.edit().putBoolean("sharing",!store.enabled()).apply();sync.configurationChanged();renderCards();return true;});
            menu.getMenu().add("Hourly sync").setCheckable(true).setChecked(store.prefs.getBoolean("hourly",true)).setOnMenuItemClickListener(item->{boolean on=!store.prefs.getBoolean("hourly",true);store.prefs.edit().putBoolean("hourly",on).apply();item.setChecked(on);sync.configurationChanged();return true;});
            menu.getMenu().add("Sync now").setOnMenuItemClickListener(item->{sync.refresh();return true;});
            menu.getMenu().add("Save deletion key").setOnMenuItemClickListener(item->{saveDeletionKey();return true;});
            menu.getMenu().add("Manage sharing").setOnMenuItemClickListener(item->{manage();return true;});
        }menu.show();
    }
    private final FriendSync.Listener observer=this::changed;
    private void changed(){
        if(!visible)return;if(connection!=null)connection.setText(sync.status);renderCards();
        if(comparison!=null&&comparison.isShowing()){FriendStore.Friend f=store.friends.get(compared);if(f!=null)fillComparison(f);else comparison.dismiss();}
    }
    void resume(){
        if(visible)return;visible=true;generation++;lastPublishDay=lastPublishRevision=-1;
        HistoryStore.get(activity).observeCounts(countObserver);sync.observe(activity,observer);handler.post(loop);renderCards();
    }
    void stop(){if(!visible)return;visible=false;HistoryStore.get(activity).unobserveCounts(countObserver);generation++;handler.removeCallbacks(loop);publishing=false;sync.unobserve(observer);}
    void destroy(){stop();if(comparison!=null)comparison.dismiss();api.close();}
    private void publish(){
        if(!visible||publishing)return;
        HistoryStore h=HistoryStore.get(activity);long day=DailyBadge.start(System.currentTimeMillis()),rev=h.revision.get();
        if(day==lastPublishDay&&rev==lastPublishRevision)return;
        publishing=true;long g=generation;
        h.dailyItems(day,DailyBadge.end(day),totals->{
            if(g!=generation)return;publishing=false;if(totals[3]==0)return;
            if(day!=DailyBadge.start(System.currentTimeMillis()))return;
            myReels=totals[0];myShorts=totals[1];myDay=FriendSnapshot.day(System.currentTimeMillis());lastPublishDay=day;lastPublishRevision=totals[2];
            if(comparison!=null&&comparison.isShowing()){FriendStore.Friend f=store.friends.get(compared);if(f!=null)fillComparison(f);}
        });
    }
    private void renderCards(){
        if(cards==null)return;
        StringBuilder key=new StringBuilder().append(store.enabled()).append(System.currentTimeMillis()/60000);
        for(FriendStore.Friend f:store.friends.values())key.append(f.id).append(f.name).append(f.day).append(f.reels).append(":").append(f.shorts).append(f.received).append(f.state);
        if(renderKey.equals(key.toString()))return;renderKey=key.toString();cards.removeAllViews();
        if(connection!=null&&!store.enabled())connection.setText(store.id().isEmpty()?"":"Sharing paused");
        if(store.friends.isEmpty()){
            LinearLayout empty=ui.card();empty.addView(ui.text("Your friends appear here",19,ui.ink,true));empty.addView(ui.text("Add someone to compare your day",14,ui.muted,false));cards.addView(empty);return;
        }
        for(FriendStore.Friend f:store.friends.values()){
            LinearLayout card=ui.card();card.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));
            LinearLayout row=ui.row();TextView avatar=avatar(f.name,40);
            LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(40));ap.setMargins(0,0,ui.dp(12),0);row.addView(avatar,ap);
            LinearLayout words=ui.column();TextView name=ui.text(f.name,17,ui.ink,true);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);words.addView(name);
            words.addView(ui.text(updated(f),11,ui.muted,false));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            LinearLayout count=ui.column();count.setPadding(ui.dp(10),0,0,0);TextView value=ui.text(f.received>0?Ui.number(f.reels+f.shorts):"—",25,ui.ink,true);value.setGravity(Gravity.END);count.addView(value);
            TextView label=ui.text("Scrolls",11,ui.muted,false);label.setGravity(Gravity.END);count.addView(label);
            if(activity.getResources().getConfiguration().fontScale>1.3f){card.addView(row);ui.gap(card,6);card.addView(count);}else{row.addView(count);card.addView(row);}
            card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(ui.dark?0x22ffffff:0x16000000),card.getBackground(),null));
            card.setFocusable(true);card.setOnClickListener(v->openComparison(f));card.setContentDescription(f.name+", "+(f.received>0?Ui.number(f.reels+f.shorts):"No shared")+" scrolls, "+updated(f)+". Tap to compare.");cards.addView(card);ui.gap(cards,10);
        }
    }
    private TextView avatar(String name,int size){
        TextView view=ui.text(name.isEmpty()?"?":new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.getDefault()),18,ui.accent,true);
        view.setGravity(Gravity.CENTER);view.setBackground(ui.shape(ui.soft,size/2));return view;
    }
    private String updated(FriendStore.Friend f){
        if(f.received==0)return "No update yet";
        String clock=android.text.format.DateFormat.getTimeFormat(activity).format(new java.util.Date(f.received));
        String date=FriendSnapshot.day(f.received).equals(FriendSnapshot.day(System.currentTimeMillis()))?"":new java.text.SimpleDateFormat("d MMM",Locale.getDefault()).format(new java.util.Date(f.received))+", ";
        return "Updated "+date+clock;
    }
    private long comparedReels=-1,comparedShorts=-1,ownReels=-1,ownShorts=-1;private String comparedDay="";private long comparedReceived=-1;private String comparedName="",comparedCalendar="";
    private void openComparison(FriendStore.Friend f){
        if(comparison!=null)comparison.dismiss();compared=f.id;comparedReels=ownReels=-1;comparison=new Dialog(activity);fillComparison(f);Window w=comparison.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(android.R.style.Animation_Dialog);}comparison.show();if(w!=null)w.setLayout(-1,-2);
    }
    private void fillComparison(FriendStore.Friend f){
        if(comparedReels==f.reels&&comparedShorts==f.shorts&&ownReels==myReels&&ownShorts==myShorts&&comparedDay.equals(f.day+myDay)&&comparedReceived==f.received&&comparedName.equals(f.name)&&comparedCalendar.equals(FriendSnapshot.day(System.currentTimeMillis())))return;
        comparedReels=f.reels;comparedShorts=f.shorts;ownReels=myReels;ownShorts=myShorts;comparedDay=f.day+myDay;comparedReceived=f.received;comparedName=f.name;comparedCalendar=FriendSnapshot.day(System.currentTimeMillis());
        LinearLayout outer=ui.column();outer.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));LinearLayout sheet=ui.card();outer.addView(sheet);
        View handle=new View(activity);handle.setBackground(ui.shape(ui.line,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.setMargins(0,0,0,ui.dp(18));sheet.addView(handle,hp);
        LinearLayout heading=ui.row();heading.addView(avatar(f.name,44),new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44)));
        LinearLayout title=ui.column();title.setPadding(ui.dp(12),0,0,0);title.addView(ui.text(f.name,22,ui.ink,true));title.addView(ui.text(updated(f),12,ui.muted,false));heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.text("×",26,ui.muted,false);close.setGravity(Gravity.CENTER);close.setContentDescription("Close comparison");close.setFocusable(true);heading.addView(close,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));close.setOnClickListener(v->comparison.dismiss());sheet.addView(heading);ui.gap(sheet,20);
        TextView period=ui.text("TODAY · SCROLLS",11,ui.muted,true);sheet.addView(period);ui.gap(sheet,10);
        boolean current=f.received>0&&f.day.equals(FriendSnapshot.day(System.currentTimeMillis()));
        boolean mineCurrent=myDay.equals(FriendSnapshot.day(System.currentTimeMillis()));
        LinearLayout totals=ui.row();totals.setGravity(Gravity.TOP);
        totals.addView(comparisonTotal("You",mineCurrent?Ui.number(myReels+myShorts):"—",ui.accent),new LinearLayout.LayoutParams(0,-2,1));
        totals.addView(comparisonTotal(f.name,current?Ui.number(f.reels+f.shorts):"—",ui.dark?0xffbaa9ee:0xff8270b5),new LinearLayout.LayoutParams(0,-2,1));sheet.addView(totals);ui.gap(sheet,16);
        if(current&&mineCurrent){
            TextView difference=ui.text(FriendSnapshot.comparison(myReels+myShorts,f.reels+f.shorts),16,ui.accent,true);difference.setPadding(ui.dp(14),ui.dp(12),ui.dp(14),ui.dp(12));difference.setBackground(ui.shape(ui.soft,14));sheet.addView(difference);ui.gap(sheet,14);
            sheet.addView(new FriendComparisonView(ui,myReels+myShorts,f.reels+f.shorts,f.name),new LinearLayout.LayoutParams(-1,ui.dp(144*Math.max(1,activity.getResources().getConfiguration().fontScale))));
        }else{sheet.addView(ui.text(f.received==0?"No shared update yet":!current?"Last shared: "+f.day+" · "+Ui.number(f.reels+f.shorts)+" scrolls":"Loading your total…",14,ui.muted,false));}
        ui.gap(sheet,18);sheet.addView(ui.button("Sync now",true,()->sync.refresh()));ui.gap(sheet,8);
        TextView remove=ui.text("Remove friend",13,ui.muted,false);remove.setGravity(Gravity.CENTER);remove.setMinHeight(ui.dp(48));remove.setFocusable(true);remove.setOnClickListener(v->remove(f));sheet.addView(remove);
        ScrollView scroll=new ScrollView(activity){@Override protected void onMeasure(int width,int height){int limit=activity.getResources().getDisplayMetrics().heightPixels*85/100;int requested=View.MeasureSpec.getSize(height);super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(requested==0?limit:Math.min(requested,limit),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(outer);scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,-2));comparison.setContentView(scroll);
        if(comparison.getWindow()!=null)comparison.getWindow().setLayout(-1,-2);
    }
    private LinearLayout comparisonTotal(String name,String value,int color){
        LinearLayout box=ui.column();TextView label=ui.text(name,13,ui.muted,false);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);box.addView(label);
        TextView count=ui.text(value,34,color,true);count.setMaxLines(1);count.setAutoSizeTextTypeUniformWithConfiguration(18,34,1,android.util.TypedValue.COMPLEX_UNIT_SP);box.addView(count,new LinearLayout.LayoutParams(-1,ui.dp(54)));return box;
    }
    private void add(){
        new AlertDialog.Builder(activity).setTitle("Add a friend").setItems(new String[]{"Create invitation","Accept invitation"},(d,i)->{if(i==0){if(store.id().isEmpty())setup(this::invite,store.server());else invite();}else acceptInput();}).setNegativeButton("Cancel",null).show();
    }
    private void setup(Runnable next,String endpointHint){
        LinearLayout c=ui.column();c.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),0);EditText name=field("Your name"),server=field("Server address");server.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);server.setText(endpointHint.isEmpty()?"https://scrollcount-friends.actionbridge.workers.dev":endpointHint);c.addView(name);c.addView(server);
        c.addView(ui.text("Accepted friends receive your daily scroll totals. This server stores your name, profile ID, friendships, public key and encrypted updates; updates expire after 7 days. Sharing uploads in the background after new counts and around each hour. You can pause it or delete your profile.",13,ui.muted,false));
        c.addView(ui.button("Privacy policy",false,()->new AlertDialog.Builder(activity).setTitle("Privacy policy").setMessage(Privacy.TEXT+ReleaseInfo.summary()).setPositiveButton("Close",null).show()));
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Your profile").setView(c).setNegativeButton("Not now",null).setPositiveButton("Agree & continue",null).create();dialog.setOnShowListener(d->dialog.getButton(-1).setOnClickListener(v->{
            String endpoint=server.getText().toString().trim().replaceAll("/$","");String n=name.getText().toString().trim();if(!FriendApi.validServer(endpoint)){server.setError("Enter the deployed HTTPS workers.dev address");return;}if(n.isEmpty()||n.length()>40){name.setError("Use 1–40 characters");return;}
            store.prefs.edit().putString("server",endpoint).apply();dialog.getButton(-1).setEnabled(false);
            api.request(store,"/register","POST",json("name",n),(r,e)->{if(activity.isDestroyed())return;dialog.getButton(-1).setEnabled(true);if(!e.isEmpty()){toast(e);return;}try{store.prefs.edit().putString("id",r.getString("id")).putString("token",r.getString("token")).putString("name",r.getString("name")).putString("deletionKey",r.optString("deletionKey","")).putBoolean("sharing",true).putBoolean("storedSharing",true).putBoolean("hourly",true).putInt("sharingDefaults",1).apply();dialog.dismiss();sync.configurationChanged();if(visible){rebuild();next.run();}}catch(JSONException ignored){toast("Invalid profile response");}});
        }));dialog.show();
    }
    private EditText field(String hint){EditText e=new EditText(activity);e.setHint(hint);e.setTextSize(15);e.setTextColor(ui.ink);e.setHintTextColor(ui.muted);e.setSingleLine(true);e.setPadding(0,ui.dp(12),0,ui.dp(12));e.setMinHeight(ui.dp(48));return e;}
    private void invite(){
        api.request(store,"/invite","POST",json("v",1),(r,e)->{
            if(activity.isDestroyed()||!visible)return;if(!e.isEmpty()){toast(e);return;}try{
                String code=FriendInvitation.link(store.server(),r.getString("code"));
                new AlertDialog.Builder(activity).setTitle("Invite a friend").setMessage("Single use · Expires in 24 hours").setNegativeButton("Close",null).setNeutralButton("Copy",(x,y)->{((ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ScrollCount invitation",code));toast("Invitation copied");}).setPositiveButton("Share",(x,y)->{Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"Join me on ScrollCount:\n"+code);activity.startActivity(Intent.createChooser(share,"Send invitation"));}).show();
            }catch(Exception ignored){toast("Could not create invitation");}
        });
    }
    private void acceptInput(){EditText input=field("Paste invitation link");input.setSingleLine(false);LinearLayout c=ui.column();c.setPadding(ui.dp(24),0,ui.dp(24),0);c.addView(input);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("Accept invitation").setView(c).setNegativeButton("Cancel",null).setPositiveButton("Check invitation",null).create();dialog.setOnShowListener(x->dialog.getButton(-1).setOnClickListener(v->{try{
            FriendInvitation p=FriendInvitation.parse(input.getText().toString());
            String endpoint=p.server,code=p.code;
            if(store.id().isEmpty()){dialog.dismiss();setup(()->preview(code),endpoint);return;}
            if(!endpoint.equals(store.server())){input.setError("This invitation uses a different server. Both profiles must use the same server.");return;}
            dialog.dismiss();preview(code);
        }catch(Exception e){input.setError("Paste a valid invitation link");}}));dialog.show();
    }
    void openInvitation(String link){
        handler.post(()->{
            if(activity.isDestroyed())return;
            try{
                FriendInvitation p=FriendInvitation.parse(link);
                if(store.id().isEmpty()){setup(()->preview(p.code),p.server);return;}
                if(!p.server.equals(store.server())){toast("This invitation uses a different sharing server");return;}
                preview(p.code);
            }catch(Exception e){toast("Invalid invitation");}
        });
    }
    private void preview(String code){
        api.request(store,"/preview","POST",json("code",code),(r,e)->{if(activity.isDestroyed()||!visible)return;if(!e.isEmpty()){toast(e);return;}
            new AlertDialog.Builder(activity).setTitle("Connect with "+r.optString("name","friend")+"?").setMessage("Share your daily scroll totals.").setNegativeButton("Cancel",null).setPositiveButton("Add friend",(d,w)->api.request(store,"/accept","POST",json("code",code),(result,error)->{if(!error.isEmpty()){toast(error);return;}try{String id=result.getString("id");store.friends.put(id,new FriendStore.Friend(id,result.getString("name")));store.save();renderCards();}catch(JSONException ignored){}toast("Friend added");sync.refresh();})).show();
        });
    }
    private void remove(FriendStore.Friend f){new AlertDialog.Builder(activity).setTitle("Remove "+f.name+"?").setMessage("Stops future sharing, deletes server snapshots between you, and removes their saved card on this phone. Previously shared totals may still exist on their phone.").setNegativeButton("Keep friend",null).setPositiveButton("Remove",(d,w)->api.request(store,"/friend","DELETE",json("id",f.id),(r,e)->{if(!e.isEmpty()){toast(e);return;}store.friends.remove(f.id);store.save();sync.configurationChanged();if(comparison!=null)comparison.dismiss();renderCards();})).show();}
    private void saveDeletionKey() {
        String key=store.prefs.getString("deletionKey","");
        if(!key.isEmpty()) { showDeletionKey(key); return; }
        api.request(store,"/deletion-key","POST",json("v",1),(r,e)->{
            if(activity.isDestroyed())return;
            if(!e.isEmpty()){toast(e);return;}
            String value=r.optString("deletionKey","");
            if(!value.matches("[a-f0-9]{64}")){toast("Invalid deletion key response");return;}
            store.prefs.edit().putString("deletionKey",value).apply();showDeletionKey(value);
        });
    }
    private void showDeletionKey(String key) {
        new AlertDialog.Builder(activity).setTitle("Keep your deletion key")
            .setMessage("Save this privately before uninstalling. It can delete your sharing profile at "+store.server()+"/delete without the app. It cannot sign in, read counts or add friends. Anyone with it can delete this profile.")
            .setNegativeButton("Close",null).setPositiveButton("Save privately",(d,w)->{
                Intent share=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,"ScrollCount deletion key\nProfile: "+store.id()+"\nDelete at: "+store.server()+"/delete\nKey: "+key+"\nKeep private: this permanently deletes your sharing profile.");
                try {activity.startActivity(Intent.createChooser(share,"Save deletion key privately"));}
                catch(ActivityNotFoundException ex){toast("No app available to save the key");}
            }).show();
    }
    private void manage(){new AlertDialog.Builder(activity).setTitle("Your sharing profile")
        .setMessage(store.prefs.getString("name","")+"\n\nEncrypted updates · 7-day retention\nSave your deletion key before uninstalling. Local history stays separate.")
        .setNegativeButton("Close",null).setNeutralButton("Save deletion key",(d,w)->saveDeletionKey())
        .setPositiveButton("Delete sharing profile",(d,w)->new AlertDialog.Builder(activity).setTitle("Delete sharing profile?")
            .setMessage("Removes your active server profile, encryption key, encrypted snapshots, invitations, friendships and cached friend totals here. Local counting history is kept. Provider recovery copies may persist for up to 30 days.")
            .setNegativeButton("Cancel",null).setPositiveButton("Delete",(x,y)->api.request(store,"/me","DELETE",null,(response,error)->{
                if(activity.isDestroyed())return;
                if(!error.isEmpty()&&!error.equals("Profile no longer exists")){toast(error);return;}
                boolean onPage=visible;stop();store.clear();sync.configurationChanged();if(onPage)rebuild();
            })).show()).show();}
    private void rebuild(){stop();body.removeAllViews();render();resume();}
    private void toast(String text){if(!activity.isDestroyed())Toast.makeText(activity,text,Toast.LENGTH_LONG).show();}
    private static JSONObject json(String key,Object value){JSONObject p=new JSONObject();try{p.put(key,value);}catch(JSONException ignored){}return p;}
}
