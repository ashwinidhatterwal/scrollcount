package in.scrollcount.probe;
import java.io.*;
public final class DiagnosticFilesTest {
    public static void main(String[] args)throws Exception {
        File cache=new File(System.getProperty("java.io.tmpdir"),"scrollcount-cache");
        File file=DiagnosticFiles.resolve(cache,"ScrollCount-diagnostics-123-abc-456.txt");
        if(!file.getParentFile().equals(new File(cache,"diagnostics")))throw new AssertionError("Attachment must remain in diagnostics directory");
        for(String unsafe:new String[]{"../history.db","/etc/passwd","ScrollCount-diagnostics-123-../../prefs.txt","ScrollCount-diagnostics-123-abc.txt/extra","ScrollCount-diagnostics-123-abc.txt%2f..","history.db","ScrollCount-diagnostics-123-abc.html"}) {
            try{DiagnosticFiles.resolve(cache,unsafe);throw new AssertionError("Unsafe attachment accepted: "+unsafe);}catch(FileNotFoundException expected){}
        }
        try{DiagnosticFiles.resolve(cache,null);throw new AssertionError("Missing name accepted");}catch(FileNotFoundException expected){}
        System.out.println("PASS: diagnostic attachment directory isolation, traversal and unsupported-file rejection");
    }
}
