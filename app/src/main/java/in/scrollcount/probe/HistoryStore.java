package in.scrollcount.probe;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
/** Ordered, off-main-thread persistence. Content identities never enter this class. */
final class HistoryStore extends SQLiteOpenHelper implements CountingEngine.Sink {
    private static HistoryStore instance;
    static synchronized HistoryStore get(Context c) {
        if(instance==null)instance=new HistoryStore(c.getApplicationContext());
        return instance;
    }
    final ExecutorService io=Executors.newSingleThreadExecutor();
    final AtomicLong revision=new AtomicLong();
    volatile String error="";
    private final java.util.concurrent.CopyOnWriteArrayList<Runnable> countListeners=new java.util.concurrent.CopyOnWriteArrayList<>();
    void observeCounts(Runnable listener){countListeners.addIfAbsent(listener);}
    void unobserveCounts(Runnable listener){countListeners.remove(listener);}
    private void countsChanged(){main.post(()->{for(Runnable listener:countListeners)CallbackGuard.run(listener,error->ServiceHealth.failure("history listener",error));});}
    private final Handler main=new Handler(Looper.getMainLooper());
    private HistoryStore(Context c) {
        super(c,"scrollcount.db",null,3);
    }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions(id TEXT PRIMARY KEY,platform TEXT NOT NULL,started_at INTEGER NOT NULL,ended_at INTEGER)");
        db.execSQL("CREATE TABLE exposures(id INTEGER PRIMARY KEY,session TEXT NOT NULL,platform TEXT NOT NULL,started_at INTEGER NOT NULL,ended_at INTEGER NOT NULL,duration INTEGER NOT NULL,classification TEXT NOT NULL)");
        db.execSQL("CREATE INDEX exposure_start ON exposures(started_at)");
        db.execSQL("CREATE INDEX exposure_session ON exposures(session)");
        createCheckpoint(db);
        createDetected(db);
    }
    private void createDetected(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE detected(session TEXT NOT NULL,platform TEXT NOT NULL,started_at INTEGER NOT NULL,PRIMARY KEY(session,started_at))");
        db.execSQL("CREATE INDEX detected_start ON detected(started_at)");
    }
    private void createCheckpoint(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE active(id INTEGER PRIMARY KEY CHECK(id=1),session TEXT NOT NULL,platform TEXT NOT NULL,started_at INTEGER NOT NULL,ended_at INTEGER NOT NULL,duration INTEGER NOT NULL,classification TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) {
        if(oldVersion<2)createCheckpoint(db);
        if(oldVersion<3) {
            createDetected(db);
            db.execSQL("INSERT OR IGNORE INTO detected(session,platform,started_at) SELECT session,platform,started_at FROM exposures");
            db.execSQL("INSERT OR IGNORE INTO detected(session,platform,started_at) SELECT session,platform,started_at FROM active WHERE duration>0 AND EXISTS(SELECT 1 FROM sessions WHERE sessions.id=active.session)");
        }
    }
    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        db.beginTransaction();
        try {
            // Commit only the last saved active duration. Never infer time during process downtime.
            db.execSQL("INSERT INTO exposures(session,platform,started_at,ended_at,duration,classification) SELECT session,platform,started_at,ended_at,duration,classification FROM active WHERE duration>0 AND EXISTS(SELECT 1 FROM sessions WHERE sessions.id=active.session)");
            db.execSQL("DELETE FROM active");
            db.execSQL("UPDATE sessions SET ended_at=COALESCE((SELECT MAX(ended_at) FROM exposures WHERE exposures.session=sessions.id),started_at) WHERE ended_at IS NULL");
            db.setTransactionSuccessful();
        }
        finally {
            db.endTransaction();
        }
    }
    private void write(Runnable action) {
        io.execute(()-> {
            try {
                action.run();
                error="";
            }
            catch(RuntimeException e) {
                error="History could not be saved. Check free phone storage.";
            }
            finally {
                revision.incrementAndGet();
            }
        });
    }
    private ContentValues item(String session,String platform,long start,long end,long duration,String category) {
        ContentValues v=new ContentValues();
        v.put("session",session);
        v.put("platform",platform);
        v.put("started_at",start);
        v.put("ended_at",end);
        v.put("duration",duration);
        v.put("classification",category);
        return v;
    }
    @Override public void startSession(String id,String platform,long start) {
        write(()-> {
            ContentValues v=new ContentValues();
            v.put("id",id);
            v.put("platform",platform);
            v.put("started_at",start);
            getWritableDatabase().insertOrThrow("sessions",null,v);
        });
    }
    @Override public void detected(String session,String platform,long start) {
        write(()-> {
            ContentValues v=new ContentValues();
            v.put("session",session);
            v.put("platform",platform);
            v.put("started_at",start);
            if(getWritableDatabase().insertWithOnConflict("detected",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)countsChanged();
        });
    }
    @Override public void checkpoint(String session,String platform,long start,long end,long duration,String category) {
        write(()-> {
            ContentValues v=item(session,platform,start,end,duration,category);
            v.put("id",1);
            getWritableDatabase().insertWithOnConflict("active",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        });
    }
    @Override public void exposure(String session,String platform,long start,long end,long duration,String category) {
        write(()-> {
            SQLiteDatabase db=getWritableDatabase();
            db.beginTransaction();
            try {
                db.insertOrThrow("exposures",null,item(session,platform,start,end,duration,category));
                db.delete("active","session=? AND started_at=?",new String[] {
                    session,String.valueOf(start)
                });
                db.setTransactionSuccessful();
            }
            finally {
                db.endTransaction();
            }
        });
    }
    @Override public void endSession(String id,long end) {
        write(()-> {
            ContentValues v=new ContentValues();
            v.put("ended_at",end);
            getWritableDatabase().update("sessions",v,"id=?",new String[] {
                id
            });
        });
    }
    void reset(java.util.function.Consumer<Boolean> done) {
        io.execute(()-> {
            boolean success=false;
            try {
                SQLiteDatabase db=getWritableDatabase();
                db.beginTransaction();
                try {
                    db.delete("active",null,null);
                    db.delete("detected",null,null);
                    db.delete("exposures",null,null);
                    db.delete("sessions",null,null);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
                success=true;
                error="";
            } catch(RuntimeException ignored) {
                error="History could not be cleared. Check free phone storage.";
            } finally {
                revision.incrementAndGet();
                final boolean cleared=success;
                if(cleared)countsChanged();
                main.post(()->done.accept(cleared));
            }
        });
    }
    static final class Day {
        long start,seen,instagram,youtube,facebook,snapchat,skips,longer,duration,sessions,longest;
    }
    static final class Session {
        String platform;
        long start,end,duration,seen,skips,longer;
        boolean open;
    }
    static final class Stats {
        long instagramItems,youtubeItems,facebookItems,snapchatItems,seen,instagram,youtube,skips,longer,duration,lifetime,lifetimeDuration,sessions,longest,first,revision,previousSeen;
        final Day[] days=new Day[7];
        final ArrayList<Session> recent=new ArrayList<>();
        Stats() {
            Calendar c=Calendar.getInstance();
            c.setTimeInMillis(midnight(System.currentTimeMillis()));
            c.add(Calendar.DAY_OF_MONTH,-6);
            for(int i=0;i<7;i++) {
                days[i]=new Day();
                days[i].start=c.getTimeInMillis();
                c.add(Calendar.DAY_OF_MONTH,1);
            }
        }
    }
    interface Result {
        void loaded(Stats stats);
    }
    void dailyItems(long start,long end,java.util.function.Consumer<long[]> result) {
        // Confirmed item records are independent of timing sessions and temporary windows.
        io.execute(()-> {
            long[] totals={0,0,0,0}; // Instagram, YouTube, revision, success
            try(Cursor rows=getReadableDatabase().rawQuery(
                    "SELECT platform,COUNT(*) FROM detected WHERE started_at>=? AND started_at<? GROUP BY platform",
                    new String[]{String.valueOf(start),String.valueOf(end)})) {
                while(rows.moveToNext()) {
                    if(!rows.getString(0).equals("YouTube"))totals[0]+=rows.getLong(1);
                    if(rows.getString(0).equals("YouTube"))totals[1]=rows.getLong(1);
                }
                totals[3]=1;
            } catch(RuntimeException ignored) {
                // Never substitute a misleading zero for unreadable history.
            }
            totals[2]=revision.get();
            main.post(()->result.accept(totals));
        });
    }
    static long midnight(long at) {
        Calendar c=Calendar.getInstance();
        c.setTimeInMillis(at);
        c.set(Calendar.HOUR_OF_DAY,0);
        c.set(Calendar.MINUTE,0);
        c.set(Calendar.SECOND,0);
        c.set(Calendar.MILLISECOND,0);
        return c.getTimeInMillis();
    }
    void load(Result result) {
        io.execute(()-> {
            Stats s=new Stats();
            try {
                Calendar c=Calendar.getInstance();
                c.setTimeInMillis(midnight(System.currentTimeMillis()));
                c.add(Calendar.DAY_OF_MONTH,-6);
                SQLiteDatabase db=getReadableDatabase();
                for(int i=0;i<7;i++) {
                    Day d=s.days[i];
                    d.start=c.getTimeInMillis();
                    c.add(Calendar.DAY_OF_MONTH,1);
                    long end=c.getTimeInMillis();
                    try(Cursor rows=db.rawQuery("SELECT platform,classification,COUNT(*),COALESCE(SUM(duration),0) FROM exposures WHERE started_at>=? AND started_at<? GROUP BY platform,classification",new String[] {
                        String.valueOf(d.start),String.valueOf(end)
                    })) {
                        while(rows.moveToNext()) {
                            String platform=rows.getString(0),category=rows.getString(1);
                            long count=rows.getLong(2);
                            d.duration+=rows.getLong(3);
                            if(category.equals("SKIPPED"))d.skips+=count;
                            else {
                                d.seen+=count;
                                if(platform.equals("Instagram"))d.instagram+=count;
                                else if(platform.equals("YouTube"))d.youtube+=count;
                                else if(platform.equals("Facebook"))d.facebook+=count;
                                else if(platform.equals("Snapchat"))d.snapchat+=count;
                                if(category.equals("LONGER"))d.longer+=count;
                            }
                        }
                    }
                    try(Cursor rows=db.rawQuery("SELECT COUNT(*),COALESCE(MAX(total),0) FROM (SELECT session,SUM(duration) AS total FROM exposures WHERE started_at>=? AND started_at<? GROUP BY session)",new String[] {
                        String.valueOf(d.start),String.valueOf(end)
                    })) {
                        if(rows.moveToFirst()) {
                            d.sessions=rows.getLong(0);
                            d.longest=rows.getLong(1);
                        }
                    }
                }
                Day today=s.days[6];
                try(Cursor rows=db.rawQuery("SELECT platform,COUNT(*) FROM detected WHERE started_at>=? AND started_at<? GROUP BY platform",new String[]{String.valueOf(today.start),String.valueOf(c.getTimeInMillis())})) {
                    while(rows.moveToNext()) {
                        if(rows.getString(0).equals("Instagram"))s.instagramItems=rows.getLong(1);
                        if(rows.getString(0).equals("YouTube"))s.youtubeItems=rows.getLong(1);
                        if(rows.getString(0).equals("Facebook"))s.facebookItems=rows.getLong(1);
                        if(rows.getString(0).equals("Snapchat"))s.snapchatItems=rows.getLong(1);
                    }
                }
                s.seen=today.seen;
                s.instagram=today.instagram;
                s.youtube=today.youtube;
                s.skips=today.skips;
                s.longer=today.longer;
                s.duration=today.duration;
                s.sessions=today.sessions;
                s.longest=today.longest;
                try(Cursor rows=db.rawQuery("SELECT COALESCE(SUM(CASE WHEN classification!='SKIPPED' THEN 1 ELSE 0 END),0),COALESCE(SUM(duration),0),COALESCE(MIN(started_at),0) FROM exposures",null)) {
                    if(rows.moveToFirst()) {
                        s.lifetime=rows.getLong(0);
                        s.lifetimeDuration=rows.getLong(1);
                        s.first=rows.getLong(2);
                    }
                }
                Calendar previous=Calendar.getInstance();
                previous.setTimeInMillis(s.days[0].start);
                previous.add(Calendar.DAY_OF_MONTH,-7);
                try(Cursor rows=db.rawQuery("SELECT COUNT(*) FROM exposures WHERE started_at>=? AND started_at<? AND classification!='SKIPPED'",new String[] {
                    String.valueOf(previous.getTimeInMillis()),String.valueOf(s.days[0].start)
                })) {
                    if(rows.moveToFirst())s.previousSeen=rows.getLong(0);
                }
                try(Cursor rows=db.rawQuery("SELECT s.platform,s.started_at,s.ended_at,COALESCE(SUM(e.duration),0),COALESCE(SUM(CASE WHEN e.classification!='SKIPPED' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN e.classification='SKIPPED' THEN 1 ELSE 0 END),0),COALESCE(SUM(CASE WHEN e.classification='LONGER' THEN 1 ELSE 0 END),0) FROM sessions s LEFT JOIN exposures e ON e.session=s.id GROUP BY s.id ORDER BY s.started_at DESC LIMIT 30",null)) {
                    while(rows.moveToNext()) {
                        Session session=new Session();
                        session.platform=rows.getString(0);
                        session.start=rows.getLong(1);
                        session.open=rows.isNull(2);
                        session.end=rows.getLong(2);
                        session.duration=rows.getLong(3);
                        session.seen=rows.getLong(4);
                        session.skips=rows.getLong(5);
                        session.longer=rows.getLong(6);
                        s.recent.add(session);
                    }
                }
                if(error.startsWith("History could not be read"))error="";
            }
            catch(RuntimeException e) {
                error="History could not be read. Check free phone storage.";
            }
            s.revision=revision.get();
            main.post(()->result.loaded(s));
        });
    }
    void export(Context context,android.net.Uri uri,java.util.function.Consumer<Boolean> done) {
        io.execute(()-> {
            boolean okay=false;
            try(OutputStream stream=context.getContentResolver().openOutputStream(uri,"wt");OutputStreamWriter out=new OutputStreamWriter(stream,java.nio.charset.StandardCharsets.UTF_8);Cursor rows=getReadableDatabase().rawQuery("SELECT platform,started_at,ended_at,duration,classification FROM exposures ORDER BY started_at",null)) {
                out.write("platform,start_epoch_ms,end_epoch_ms,active_duration_ms,category\n");
                while(rows.moveToNext())out.write(rows.getString(0)+","+rows.getLong(1)+","+rows.getLong(2)+","+rows.getLong(3)+","+rows.getString(4)+"\n");
                okay=true;
            }
            catch(Exception ignored) {
            }
            final boolean success=okay;
            main.post(()->done.accept(success));
        });
    }
}
