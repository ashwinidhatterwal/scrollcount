package in.scrollcount.probe;
/** Isolate recoverable callback failures. Fatal VM errors are never swallowed. */
final class CallbackGuard {
    interface Failure { void failed(RuntimeException failure); }
    static void run(Runnable callback,Failure failure) {
        try { callback.run(); }
        catch(RuntimeException error) {
            try { failure.failed(error); } catch(RuntimeException ignored) { /* Recovery must not recurse. */ }
        }
    }
}
