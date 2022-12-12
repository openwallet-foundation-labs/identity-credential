/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.identity;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Class used for logging.
 *
 * <p>By default debug messages are suppressed. The application can control this using the
 * {@link #setDebugEnabled(boolean)} method.
 *
 * <p>On Android it uses the underlying {@link android.util.Log} primitives and on non-Android
 * environments it prints the message on standard output.
 */
public class Logger {
    private static final String TAG = "Logger";
    private static final int LEVEL_D = 0;
    private static final int LEVEL_I = 1;
    private static final int LEVEL_W = 2;
    private static final int LEVEL_E = 3;

    private static boolean mDebugEnabled = true;   // TODO: make false by default
    private static FileWriter mFileWriter = null;
    private static String mFileWriterPath = null;

    public static void startLoggingToFile(File logFile) throws IOException {
        if (mFileWriter != null) {
            Logger.w(TAG, "startLoggingToFile: Already logging to file " + mFileWriterPath);
            mFileWriter.close();
            mFileWriter = null;
            mFileWriterPath = null;
        }
        mFileWriterPath = logFile.getAbsolutePath();
        Logger.d(TAG, "Starting logging to file " + mFileWriterPath);
        mFileWriter = new FileWriter(logFile, false);
    }

    public static void stopLoggingToFile() throws IOException {
        if (mFileWriter == null) {
            Logger.w(TAG, "startLoggingToFile: Not logging to file");
            return;
        }
        mFileWriter.close();
        mFileWriter = null;
        Logger.d(TAG, "Stopped logging to file " + mFileWriterPath);
        mFileWriterPath = null;
    }

    // TODO: make it possible for application to supply its own logging method.

    private static String prepareLine(int level, @NonNull String tag, @NonNull String msg, @Nullable Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSS", Locale.US)
                .format(new java.util.Date());
        sb.append(timeStamp);
        sb.append(": ");
        switch (level) {
            case LEVEL_D:
                sb.append("DEBUG");
                break;
            case LEVEL_I:
                sb.append("INFO");
                break;
            case LEVEL_W:
                sb.append("WARNING");
                break;
            case LEVEL_E:
                sb.append("ERROR");
                break;
        }
        sb.append(": ");
        sb.append(tag);
        sb.append(": ");
        sb.append(msg);
        if (throwable != null) {
            sb.append("\nEXCEPTION: ");
            sb.append(throwable.toString());
        }
        return sb.toString();
    }

    private static void println(int level, @NonNull String tag, @NonNull String msg, @Nullable Throwable throwable) {
        String logLine = null;

        if (Build.VERSION.SDK_INT > 0) {
            switch (level) {
                case LEVEL_D:
                    if (throwable != null) {
                        android.util.Log.d(tag, msg);
                    } else {
                        android.util.Log.d(tag, msg, throwable);
                    }
                    break;
                case LEVEL_I:
                    if (throwable != null) {
                        android.util.Log.i(tag, msg);
                    } else {
                        android.util.Log.i(tag, msg, throwable);
                    }
                    break;
                case LEVEL_W:
                    if (throwable != null) {
                        android.util.Log.w(tag, msg);
                    } else {
                        android.util.Log.w(tag, msg, throwable);
                    }
                    break;
                case LEVEL_E:
                    if (throwable != null) {
                        android.util.Log.e(tag, msg);
                    } else {
                        android.util.Log.e(tag, msg, throwable);
                    }
                    break;
            }
        } else {
            logLine = prepareLine(level, tag, msg, throwable);
            System.out.println(logLine);
        }

        if (mFileWriter != null) {
            if (logLine == null) {
                logLine = prepareLine(level, tag, msg, throwable);
            }
            try {
                mFileWriter.write(logLine);
                mFileWriter.write('\n');
            } catch (IOException e) {
                if (Build.VERSION.SDK_INT > 0) {
                    android.util.Log.e(tag, "Error writing log message to file", e);
                } else {
                    System.out.println("Error writing log message to file: " + e);
                }
                e.printStackTrace();
            }
        }
    }

    public static boolean isDebugEnabled() {
        return mDebugEnabled;
    }

    public static void setDebugEnabled(boolean enabled) {
        mDebugEnabled = enabled;
    }

    public static void d(@NonNull String tag, @NonNull String msg) {
        if (isDebugEnabled()) {
            println(LEVEL_D, tag, msg, null);
        }
    }

    public static void d(@NonNull String tag, @NonNull String msg, @NonNull Throwable throwable) {
        if (isDebugEnabled()) {
            println(LEVEL_D, tag, msg, throwable);
        }
    }

    public static void w(@NonNull String tag, @NonNull String msg) {
        println(LEVEL_W, tag, msg, null);
    }

    public static void w(@NonNull String tag, @NonNull String msg, @NonNull Throwable throwable) {
        println(LEVEL_W, tag, msg, throwable);
    }

    public static void e(@NonNull String tag, @NonNull String msg) {
        println(LEVEL_E, tag, msg, null);
    }

    public static void e(@NonNull String tag, @NonNull String msg, @NonNull Throwable throwable) {
        println(LEVEL_E, tag, msg, throwable);
    }

    private static void hex(int level, @NonNull String tag, @NonNull String message, @NonNull byte[] data) {
        StringBuilder sb = new StringBuilder();
        sb.append(message).append(String.format(Locale.US, ": %d bytes of data: ", data.length));
        sb.append(Util.toHex(data));
        println(level, tag, sb.toString(), null);
    }

    public static void dHex(@NonNull String tag, @NonNull String message, @NonNull byte[] data) {
        if (isDebugEnabled()) {
            hex(LEVEL_D, tag, message, data);
        }
    }

    public static void iHex(@NonNull String tag, @NonNull String message, @NonNull byte[] data) {
        if (isDebugEnabled()) {
            hex(LEVEL_I, tag, message, data);
        }
    }

    public static void wHex(@NonNull String tag, @NonNull String message, @NonNull byte[] data) {
        hex(LEVEL_W, tag, message, data);
    }

    public static void eHex(@NonNull String tag, @NonNull String message, @NonNull byte[] data) {
        hex(LEVEL_E, tag, message, data);
    }

    private static void cbor(int level, @NonNull String tag, @NonNull String message, @NonNull byte[] encodedCbor) {
        StringBuilder sb = new StringBuilder();
        sb.append(message).append(String.format(Locale.US, ": %d bytes of CBOR: ", encodedCbor.length));
        sb.append(Util.toHex(encodedCbor));
        sb.append("\n");
        sb.append("In diagnostic notation:\n");
        sb.append(CborUtil.toDiagnostics(encodedCbor,
                CborUtil.DIAGNOSTICS_FLAG_PRETTY_PRINT | CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR));
        println(level, tag, sb.toString(), null);
    }

    public static void dCbor(@NonNull String tag, @NonNull String message, @NonNull byte[] encodedCbor) {
        if (isDebugEnabled()) {
            cbor(LEVEL_D, tag, message, encodedCbor);
        }
    }

    public static void iCbor(@NonNull String tag, @NonNull String message, @NonNull byte[] encodedCbor) {
        cbor(LEVEL_I, tag, message, encodedCbor);
    }

    public static void wCbor(@NonNull String tag, @NonNull String message, @NonNull byte[] encodedCbor) {
        cbor(LEVEL_W, tag, message, encodedCbor);
    }

    public static void eCbor(@NonNull String tag, @NonNull String message, @NonNull byte[] encodedCbor) {
        cbor(LEVEL_E, tag, message, encodedCbor);
    }
}
