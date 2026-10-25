package com.gathin.tvhome;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Ultra-lean ContentProvider serving downloaded add-on APKs to the Android PackageInstaller
 * without adding third-party dependencies or multi-megabyte libraries.
 */
public class CleanTVFileProvider extends ContentProvider {

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
        Context ctx = getContext();
        if (ctx == null) throw new FileNotFoundException("No context available");

        String filename = uri.getLastPathSegment();
        if (filename == null || filename.contains("..")) {
            throw new FileNotFoundException("Invalid filename: " + filename);
        }

        File cacheFile = new File(ctx.getCacheDir(), filename);
        if (cacheFile.exists()) {
            return ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY);
        }

        File extCache = ctx.getExternalCacheDir();
        if (extCache != null) {
            File extFile = new File(extCache, filename);
            if (extFile.exists()) {
                return ParcelFileDescriptor.open(extFile, ParcelFileDescriptor.MODE_READ_ONLY);
            }
        }

        throw new FileNotFoundException("File not found for URI: " + uri);
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "application/vnd.android.package-archive";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
