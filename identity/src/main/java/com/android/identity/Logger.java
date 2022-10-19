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
import android.telecom.Call;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.concurrent.Callable;

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
    private static boolean mDebugEnabled = true;   // TODO: make false by default

    private static final int LEVEL_D = 0;
    private static final int LEVEL_W = 1;
    private static final int LEVEL_E = 2;

    // TODO: make it possible for application to supply its own logging method.

    private static void println(int level, @NonNull String tag, @NonNull String msg, @Nullable Throwable throwable) {
        if (Build.VERSION.SDK_INT > 0) {
            switch (level) {
                case LEVEL_D:
                    if (throwable != null) {
                        android.util.Log.d(tag, msg);
                    } else {
                        android.util.Log.d(tag, msg, throwable);
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
            StringBuilder sb = new StringBuilder();
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss.SSS").format(new java.util.Date());
            sb.append(timeStamp);
            sb.append(": ");
            switch (level) {
                case LEVEL_D:
                    sb.append("DEBUG");
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
            System.out.println(sb);
        }
    }

    public static boolean isDebugEnabled() {
        return mDebugEnabled;
    }

    private static void setDebugEnabled(boolean enabled) {
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
}
