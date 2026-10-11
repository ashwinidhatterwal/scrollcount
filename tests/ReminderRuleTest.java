package in.scrollcount.probe;
public final class ReminderRuleTest {
 public static void main(String[] args){if(ReminderRule.due(0,9999999,false,false) || ReminderRule.due(15,899999,false,false) || ReminderRule.due(15,900000,true,false) || ReminderRule.due(15,900000,false,true) || !ReminderRule.due(15,900000,false,false))throw new AssertionError("opt-in, threshold, paused or duplicate rule");System.out.println("PASS: reminder opt-in, exact threshold, paused time and one-per-session suppression");}
}
