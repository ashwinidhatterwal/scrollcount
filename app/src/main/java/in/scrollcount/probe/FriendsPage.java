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
    private final Runnable loop=new Runnable(){public void run(){if(!visible)return;publish();renderCards();handler.postDelayed(this,60000);}};
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
            LinearLayout card=ui.card(),row=ui.row();TextView avatar=ui.text(f.name.isEmpty()?"?":new String(Character.toChars(f.name.codePointAt(0))).toUpperCase(Locale.getDefault()),22,ui.accent,true);avatar.setGravity(Gravity.CENTER);avatar.setBackground(ui.shape(ui.soft,22));LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(ui.dp(44),ui.dp(44));ap.setMargins(0,0,ui.dp(12),0);row.addView(avatar,ap);
            LinearLayout words=ui.column();words.addView(ui.text(f.name,19,ui.ink,true));row.addView(words,new LinearLayout.LayoutParams(0,-2,1));row.addView(ui.text("›",26,ui.muted,false));card.addView(row);ui.gap(card,16);
            LinearLayout values=ui.row();values.addView(counter("Reels",f.received>0?Ui.number(f.reels):"—"),new LinearLayout.LayoutParams(0,-2,1));values.addView(counter("Shorts",f.received>0?Ui.number(f.shorts):"—"),new LinearLayout.LayoutParams(0,-2,1));card.addView(values);ui.gap(card,12);
            card.addView(ui.text(updated(f),12,ui.muted,false));card.setFocusable(true);card.setOnClickListener(v->openComparison(f));card.setContentDescription(f.name+", Reels "+(f.received>0?f.reels:"not shared")+", Shorts "+(f.received>0?f.shorts:"not shared")+", "+updated(f)+". Tap to compare.");cards.addView(card);ui.gap(cards,14);
        }
    }
    private LinearLayout counter(String label,String value){LinearLayout c=ui.column();c.addView(ui.text(label,12,ui.muted,false));c.addView(ui.text(value,30,ui.ink,true));return c;}
    private String updated(FriendStore.Friend f){
        if(f.received==0)return "No update yet";
        long minutes=Math.max(0,System.currentTimeMillis()-f.received)/60000;
        String age=minutes<1?"just now":minutes<60?minutes+" min ago":minutes<1440?minutes/60+"h ago":minutes/1440+" days ago";
        return (f.day.equals(FriendSnapshot.day(System.currentTimeMillis()))?"Today":f.day)+" · "+age;
    }
    private long comparedReels=-1,comparedShorts=-1,ownReels=-1,ownShorts=-1;private String comparedDay="";private long comparedReceived=-1;
    private void openComparison(FriendStore.Friend f){
        compared=f.id;comparedReels=ownReels=-1;comparison=new Dialog(activity);fillComparison(f);Window w=comparison.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.setWindowAnimations(android.R.style.Animation_Dialog);}comparison.show();if(w!=null)w.setLayout(-1,-2);
    }
    private void fillComparison(FriendStore.Friend f){
        if(comparedReels==f.reels&&comparedShorts==f.shorts&&ownReels==myReels&&ownShorts==myShorts&&comparedDay.equals(f.day+myDay)&&comparedReceived==f.received)return;
        comparedReels=f.reels;comparedShorts=f.shorts;ownReels=myReels;ownShorts=myShorts;comparedDay=f.day+myDay;comparedReceived=f.received;
        LinearLayout outer=ui.column();outer.setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));LinearLayout sheet=ui.card();outer.addView(sheet);
        View handle=new View(activity);handle.setBackground(ui.shape(ui.line,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(40),ui.dp(4));hp.gravity=Gravity.CENTER_HORIZONTAL;hp.setMargins(0,0,0,ui.dp(18));sheet.addView(handle,hp);
        LinearLayout heading=ui.row();heading.addView(ui.text("You & "+f.name,24,ui.ink,true),new LinearLayout.LayoutParams(0,-2,1));TextView close=ui.text("×",28,ui.muted,false);close.setGravity(Gravity.CENTER);close.setContentDescription("Close comparison");heading.addView(close,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));close.setOnClickListener(v->comparison.dismiss());sheet.addView(heading);
        if(f.received>0&&f.day.equals(myDay)){
            TextView difference=ui.text(FriendSnapshot.comparison(myReels+myShorts,f.reels+f.shorts),18,ui.accent,true);sheet.addView(difference);ui.gap(sheet,16);
            sheet.addView(new FriendComparisonView(ui,myReels+myShorts,f.reels+f.shorts,f.name),new LinearLayout.LayoutParams(-1,ui.dp(136*Math.max(1,activity.getResources().getConfiguration().fontScale))));
            LinearLayout table=ui.row();table.addView(ui.text("",13,ui.muted,false),new LinearLayout.LayoutParams(0,-2,1));table.addView(tableValue("You"),new LinearLayout.LayoutParams(0,-2,1));table.addView(tableValue(f.name),new LinearLayout.LayoutParams(0,-2,1));sheet.addView(table);
            comparisonRow(sheet,"Reels",myReels,f.reels);comparisonRow(sheet,"Shorts",myShorts,f.shorts);
        }else{ui.gap(sheet,16);sheet.addView(ui.text(f.received==0?"No shared update yet":"Waiting for today’s update",16,ui.muted,false));}
        ui.gap(sheet,14);sheet.addView(ui.text(updated(f),12,ui.muted,false));ui.gap(sheet,16);sheet.addView(ui.button("Remove friend",false,()->remove(f)));
        ScrollView scroll=new ScrollView(activity){@Override protected void onMeasure(int width,int height){int limit=activity.getResources().getDisplayMetrics().heightPixels*85/100;int requested=View.MeasureSpec.getSize(height);super.onMeasure(width,View.MeasureSpec.makeMeasureSpec(requested==0?limit:Math.min(requested,limit),View.MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.addView(outer);scroll.setLayoutParams(new android.view.ViewGroup.LayoutParams(-1,-2));comparison.setContentView(scroll);
        if(comparison.getWindow()!=null)comparison.getWindow().setLayout(-1,-2);
    }
    private TextView tableValue(String text){TextView t=ui.text(text,15,ui.ink,true);t.setGravity(Gravity.END);t.setMaxLines(1);t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
    private void comparisonRow(LinearLayout parent,String label,long mine,long theirs){LinearLayout row=ui.row();row.setPadding(0,ui.dp(10),0,ui.dp(10));row.addView(ui.text(label,14,ui.muted,false),new LinearLayout.LayoutParams(0,-2,1));row.addView(tableValue(Ui.number(mine)),new LinearLayout.LayoutParams(0,-2,1));row.addView(tableValue(Ui.number(theirs)),new LinearLayout.LayoutParams(0,-2,1));parent.addView(row);}
    private void add(){
        new AlertDialog.Builder(activity).setTitle("Add a friend").setItems(new String[]{"Create invitation","Accept invitation"},(d,i)->{if(i==0){if(store.id().isEmpty())setup(this::invite,store.server());else invite();}else acceptInput();}).setNegativeButton("Cancel",null).show();
    }
    private void setup(Runnable next,String endpointHint){
        LinearLayout c=ui.column();c.setPadding(ui.dp(24),ui.dp(12),ui.dp(24),0);EditText name=field("Your name"),server=field("Server address");server.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);server.setText(endpointHint.isEmpty()?"https://scrollcount-friends.actionbridge.workers.dev":endpointHint);c.addView(name);c.addView(server);
        c.addView(ui.text("Accepted friends receive your daily Reels and Shorts totals. This server stores your name, profile ID, friendships, public key and encrypted updates; updates expire after 7 days. Sharing uploads in the background after new counts and around each hour. You can pause it or delete your profile.",13,ui.muted,false));
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
            new AlertDialog.Builder(activity).setTitle("Connect with "+r.optString("name","friend")+"?").setMessage("Share your daily Reels and Shorts totals.").setNegativeButton("Cancel",null).setPositiveButton("Add friend",(d,w)->api.request(store,"/accept","POST",json("code",code),(result,error)->{if(!error.isEmpty()){toast(error);return;}try{String id=result.getString("id");store.friends.put(id,new FriendStore.Friend(id,result.getString("name")));store.save();renderCards();}catch(JSONException ignored){}toast("Friend added");sync.refresh();})).show();
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
