package in.scrollcount.probe;
import java.io.File;
/** Only generated diagnostic attachments may be exposed through the read-only provider. */
final class DiagnosticFiles {
    static File resolve(File cache,String name) throws java.io.FileNotFoundException {
        if(name==null || !name.matches("ScrollCount-diagnostics-[0-9]+-[a-f0-9-]+\\.txt"))
            throw new java.io.FileNotFoundException("Invalid diagnostic attachment");
        return new File(new File(cache,"diagnostics"),name);
    }
}
