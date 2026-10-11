package in.scrollcount.probe;
public final class TransitionProbeTest {
  static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
  public static void main(String[] args) {
    TransitionProbe p=new TransitionProbe();
    p.observe("a",0,true); p.observe("a",650,true);
    check(p.accepted==0,"entry is baseline, not transition");
    p.observe("b",1000,true); p.observe("b",1649,true);
    check(p.accepted==0,"unstable candidate must wait");
    p.observe("b",1650,true); p.observe("b",3000,true);
    check(p.accepted==1,"stable transition counted exactly once");
    p.observe("c",4000,true); p.observe("b",4300,true); p.observe("b",5000,true);
    check(p.accepted==1,"transient changes cancel");
    p.observe("c",6000,true); p.observe("c",6700,false);
    p.observe("b",8000,true); check(p.accepted==1,"overlay does not count");
    p.observe(null,9000,true); check(p.accepted==1,"missing fingerprint does not count");
    p.observe("a",10000,true); p.observe("a",10650,true);
    check(p.accepted==2,"backward change is a new exposure transition");
    p.reset(); check(p.accepted==0,"reset clears counts");
    p.observe("z",12000,true); p.observe("z",12650,true);
    check(p.accepted==0,"reset starts fresh baseline");
    p.observe("caption-expanded",13000,true,false); p.observe("caption-expanded",14000,true,false);
    check(p.accepted==0,"content mutation without pager movement cannot count");
    p.observe("pager:1",15000,true,true); p.observe("pager:1",15650,true,true);
    check(p.accepted==1,"verified pager movement can count");
    p.observe("pager:1",17000,true,false); check(p.accepted==1,"same position never duplicates");
    p.observe("pager:2",18000,true,true); p.observe("pager:2",18650,true,true);
    check(p.accepted==2,"next position counts even with identical caption");
    System.out.println("PASS: baseline, stability boundary, duplicate, transient, overlay, missing, backward, reset");
  }
}
