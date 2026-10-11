"""Validate the app's exact schema, aggregates, v1 upgrade and atomic checkpoint recovery."""
import re, sqlite3, tempfile
from pathlib import Path
source=Path(__file__).resolve().parents[1]/'app/src/main/java/in/scrollcount/probe/HistoryStore.java'
text=source.read_text()
statements=re.findall(r'db\.execSQL\("([^"\n]+)"\)',text)
queries=re.findall(r'(?:db|getReadableDatabase\(\))\.rawQuery\(\s*"([^"\n]+)"',text)
def query(prefix):return next(q for q in queries if q.startswith(prefix))
creates=[s for s in statements if s.startswith('CREATE')]
migrations=[s for s in statements if s.startswith('INSERT OR IGNORE INTO detected')]
recovery=[s for s in statements if not s.startswith('CREATE') and s not in migrations]
with tempfile.TemporaryDirectory() as root:
 path=Path(root)/'history.db';db=sqlite3.connect(path)
 # A version-1 database has all historical tables/indexes and no active checkpoint.
 for sql in creates:
  if not sql.startswith(('CREATE TABLE active','CREATE TABLE detected','CREATE INDEX detected')):db.execute(sql)
 db.executemany('INSERT INTO sessions VALUES(?,?,?,NULL)', [('s1','Instagram',110000),('s2','YouTube',130000)])
 db.executemany('INSERT INTO exposures(session,platform,started_at,ended_at,duration,classification) VALUES(?,?,?,?,?,?)',[
 ('s1','Instagram',110000,114000,4000,'SEEN'),('s1','Instagram',120000,121000,1000,'SKIPPED'),('s2','YouTube',130000,145000,15000,'LONGER'),('s1','Instagram',1000,4000,3000,'SEEN')])
 db.execute(next(sql for sql in creates if sql.startswith('CREATE TABLE active')))
 assert db.execute('SELECT COUNT(*) FROM exposures').fetchone()==(4,),'upgrade preserves v1 rows'
 for sql in creates:
  if sql.startswith(('CREATE TABLE detected','CREATE INDEX detected')):db.execute(sql)
 for sql in migrations:db.execute(sql)
 daily=query('SELECT platform,COUNT(*) FROM detected')
 assert dict(db.execute(daily,('100000','200000')))=={'Instagram':2,'YouTube':1},'daily badge includes slow and quick exposures across sessions'
 assert list(db.execute(daily,('200000','300000')))==[],'new day starts empty'
 today=query('SELECT platform,classification,COUNT(*)')
 rows=list(db.execute(today,('100000','200000')))
 assert sum(count for _,cat,count,_ in rows if cat!='SKIPPED')==2
 assert sum(duration for *_,duration in rows)==20000
 assert db.execute(query('SELECT COUNT(*),COALESCE(MAX(total)'),('100000','200000')).fetchone()==(2,15000)
 lifetime=query("SELECT COALESCE(SUM(CASE WHEN classification!='SKIPPED'")
 assert db.execute(lifetime).fetchone()==(3,23000,1000)
 assert db.execute(query('SELECT COUNT(*) FROM exposures WHERE'),('0','100000')).fetchone()==(1,)
 recent=query('SELECT s.platform,s.started_at')
 assert list(db.execute(recent))[0][0]=='YouTube'
 # Updating a checkpoint replaces one active row rather than accumulating views.
 db.execute('INSERT OR IGNORE INTO detected VALUES(?,?,?)',('s2','YouTube',160000))
 db.execute('INSERT OR IGNORE INTO detected VALUES(?,?,?)',('s2','YouTube',160000))
 assert db.execute('SELECT COUNT(*) FROM detected').fetchone()==(5,),'same item cannot be counted twice'
 db.execute('INSERT OR REPLACE INTO active VALUES(1,?,?,?,?,?,?)',('s2','YouTube',160000,164000,4000,'SEEN'))
 db.execute('INSERT OR REPLACE INTO active VALUES(1,?,?,?,?,?,?)',('s2','YouTube',160000,166000,6000,'SEEN'))
 assert db.execute('SELECT COUNT(*) FROM exposures').fetchone()==(4,),'checkpoint is not a finalized view'
 db.commit();db.close();db=sqlite3.connect(path)
 with db:
  for sql in recovery:db.execute(sql)
 assert db.execute('SELECT COUNT(*) FROM exposures').fetchone()==(5,),'recover only saved checkpoint'
 assert dict(db.execute('SELECT id,ended_at FROM sessions'))=={'s1':121000,'s2':166000},'no downtime invented'
 with db:
  for sql in recovery:db.execute(sql)
 assert db.execute('SELECT COUNT(*) FROM exposures').fetchone()==(5,),'reopen cannot double recover'
 assert db.execute('SELECT COUNT(*) FROM detected').fetchone()==(5,),'daily count survives reopening independently of checkpoint recovery'
 # A finalized item and removal of its active checkpoint form one transaction.
 db.execute('INSERT OR REPLACE INTO active VALUES(1,?,?,?,?,?,?)',('s1','Instagram',180000,181000,1000,'SKIPPED'))
 db.commit()
 with db:
  db.execute('INSERT INTO exposures(session,platform,started_at,ended_at,duration,classification) SELECT session,platform,started_at,ended_at,duration,classification FROM active')
  db.execute('DELETE FROM active WHERE session=? AND started_at=?',('s1','180000'))
 with db:
  for sql in recovery:db.execute(sql)
 assert db.execute('SELECT COUNT(*) FROM exposures').fetchone()==(6,),'finished checkpoint not recovered twice'
 export=query('SELECT platform,started_at,ended_at')
 assert len(list(db.execute(export)))==6
 # Rollback preserves a checkpoint if finalization fails before commit.
 db.execute('INSERT OR REPLACE INTO active VALUES(1,?,?,?,?,?,?)',('s1','Instagram',190000,192000,2000,'SEEN'));db.commit()
 try:
  with db:
   db.execute('DELETE FROM active');raise ValueError('simulate interrupted transaction')
 except ValueError:pass
 assert db.execute('SELECT COUNT(*) FROM active').fetchone()==(1,)
 with db:
  db.execute('DELETE FROM active');db.execute('DELETE FROM detected');db.execute('DELETE FROM exposures');db.execute('DELETE FROM sessions')
 assert db.execute(lifetime).fetchone()==(0,0,0)
 assert list(db.execute(daily,('100000','200000')))==[],'history reset clears badge totals'
 # Resume detection after deletion: a new session and item count from zero.
 # Execute the application's table-deletion order, including repeated deletion.
 reset=text[text.index('    void reset('):text.index('    static final class Day')]
 tables=re.findall(r'db.delete\("([^"\n]+)",null,null\)',reset)
 assert tables==['active','detected','exposures','sessions']
 db.execute("INSERT INTO sessions VALUES('resumed','YouTube',150000,NULL)")
 db.execute("INSERT INTO detected VALUES('resumed','YouTube',150000)")
 assert dict(db.execute(daily,('100000','200000')))=={'YouTube':1}
 db.commit()
 try:
  with db:
   for table in tables:
    db.execute('DELETE FROM '+table)
    if table=='exposures':raise ValueError('failed deletion')
 except ValueError:pass
 assert dict(db.execute(daily,('100000','200000')))=={'YouTube':1},'failed delete retains daily history atomically'
 for repeat in range(2):
  with db:
   for table in tables:db.execute('DELETE FROM '+table)
 assert list(db.execute(daily,('100000','200000')))==[]
 db.execute("INSERT INTO sessions VALUES('resumed-again','Instagram',160000,NULL)")
 db.execute("INSERT INTO detected VALUES('resumed-again','Instagram',160000)")
 assert dict(db.execute(daily,('100000','200000')))=={'Instagram':1},'counting works after repeated deletion'
 db.close()
# A v2 database includes a live checkpoint before the detection-ledger upgrade.
v2=sqlite3.connect(':memory:')
for sql in creates:
 if not sql.startswith(('CREATE TABLE detected','CREATE INDEX detected')):v2.execute(sql)
v2.execute("INSERT INTO sessions VALUES('old','YouTube',100000,NULL)")
v2.execute("INSERT INTO exposures(session,platform,started_at,ended_at,duration,classification) VALUES('old','YouTube',100000,101000,1000,'SKIPPED')")
v2.execute("INSERT INTO active VALUES(1,'old','YouTube',102000,105000,3000,'SEEN')")
for sql in creates:
 if sql.startswith(('CREATE TABLE detected','CREATE INDEX detected')):v2.execute(sql)
for sql in migrations:v2.execute(sql)
assert dict(v2.execute(daily,('100000','200000')))=={'YouTube':2},'v2 upgrade preserves finished and active items'
for sql in migrations:v2.execute(sql)
for sql in recovery:v2.execute(sql)
assert dict(v2.execute(daily,('100000','200000')))=={'YouTube':2},'upgrade/recovery cannot count active item twice'
v2.execute("INSERT OR IGNORE INTO detected VALUES('new','YouTube',110000)")
assert dict(v2.execute(daily,('100000','200000')))=={'YouTube':3},'confirmed item persists without a finished timing row'
v2.close()
print('PASS: v1/v2→v3 preservation, detected-item migration and durability, daily/weekly/session aggregates, replaceable checkpoint, exact-once recovery, rollback, export and deletion')
