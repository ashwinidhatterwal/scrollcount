package in.scrollcount.probe;
public final class FriendSnapshotTest {
 public static void main(String[] args){
  check(FriendSnapshot.valid("2026-10-08",0,200));
  check(!FriendSnapshot.valid("2026-02-30",1,2));
  check(!FriendSnapshot.valid("2026-10-08",-1,2));
  check(!FriendSnapshot.valid("2026-10-08",10000001,2));
  check(!FriendSnapshot.valid("2026-10-08junk",1,2));
  check(FriendSnapshot.comparison(10,10).equals("A perfect tie!"));
  check(FriendSnapshot.comparison(0,10).contains("You scrolled"));
  check(FriendSnapshot.comparison(10,0).contains("Your friend"));
  System.out.println("PASS: strict peer snapshot dates/count bounds and comparison direction/tie");
 }
 static void check(boolean condition){if(!condition)throw new AssertionError();}
}
