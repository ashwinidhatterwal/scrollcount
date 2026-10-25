package in.scrollcount.probe;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;
/** Per-URI temporary read grants; never exposes history, preferences or arbitrary files. */
public final class DiagnosticProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private File file(Uri uri) throws FileNotFoundException {
        if(!"content".equals(uri.getScheme()) || !(getContext().getPackageName()+".diagnostics").equals(uri.getAuthority())
                || uri.getPathSegments().size()!=1)throw new FileNotFoundException("Unknown attachment");
        return DiagnosticFiles.resolve(getContext().getCacheDir(),uri.getLastPathSegment());
    }
    @Override public String getType(Uri uri){return "text/plain";}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws FileNotFoundException {
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        try {
            File f=file(uri);if(!f.isFile())return null;
            String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            MatrixCursor cursor=new MatrixCursor(columns);Object[] values=new Object[columns.length];
            for(int i=0;i<columns.length;i++)values[i]=OpenableColumns.DISPLAY_NAME.equals(columns[i])?f.getName():OpenableColumns.SIZE.equals(columns[i])?f.length():null;
            cursor.addRow(values);return cursor;
        }catch(FileNotFoundException e){return null;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read only");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read only");}
}
