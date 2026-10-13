package in.scrollcount.probe;
/** Failure injection at the boundary used by service callbacks and optional listeners. */
public final class CallbackGuardTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        int[] calls={0,0};
        CallbackGuard.run(()->{throw new IllegalStateException("private content must not escape");},error->{calls[0]++;check(error instanceof IllegalStateException,"original error available for sanitized diagnosis");});
        CallbackGuard.run(()->calls[1]++,error->{throw new AssertionError("healthy callback must not trigger recovery");});
        check(calls[0]==1&&calls[1]==1,"next event still runs after failed callback");
        CallbackGuard.run(()->{throw new IllegalArgumentException();},error->{throw new IllegalStateException("failed recovery");});
        boolean fatal=false;
        try{CallbackGuard.run(()->{throw new OutOfMemoryError();},error->calls[0]++);}catch(OutOfMemoryError expected){fatal=true;}
        check(fatal&&calls[0]==1,"fatal VM errors propagate, never fake recovery");
        int[] optional={0};
        Runnable[] listeners={()->{throw new IllegalStateException();},()->optional[0]++};
        for(Runnable listener:listeners)CallbackGuard.run(listener,error->{});
        check(optional[0]==1,"one bad optional listener does not block the next");
        System.out.println("PASS: callback fault isolation, next-event recovery, failed-recovery isolation, independent listeners and fatal-error propagation");
    }
}
