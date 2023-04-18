package com.android.identity.android.util

import android.util.Log
import com.android.identity.util.Logger.LEVEL_D
import com.android.identity.util.Logger.LEVEL_E
import com.android.identity.util.Logger.LEVEL_I
import com.android.identity.util.Logger.LEVEL_W
import com.android.identity.util.Logger.LogPrinter

/** Android implementation of [LogPrinter] printing to logcat. */
class AndroidLogPrinter : LogPrinter {

   override fun printLn(level: Int, tag: String, msg: String, throwable: Throwable?) {
      when (level) {
         LEVEL_D -> throwable?.let { Log.d(tag, msg, it) } ?: Log.d(tag, msg)
         LEVEL_I -> throwable?.let { Log.i(tag, msg, it) } ?: Log.i(tag, msg)
         LEVEL_W -> throwable?.let { Log.w(tag, msg, it) } ?: Log.w(tag, msg)
         LEVEL_E -> throwable?.let { Log.e(tag, msg, it) } ?: Log.e(tag, msg)
      }
   }
}
