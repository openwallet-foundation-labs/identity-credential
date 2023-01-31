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
 
package com.android.identity.wwwreader;
 
//import androidx.annotation.NonNull;
 
/**
* Represents a single instant in time. Ideally, we'd use {@code java.time.Instant}, but we cannot
* do so until we move to API level 26.
*/
public final class Timestamp {
   private final long mEpochMillis;
 
   private Timestamp(long epochMillis) {
       mEpochMillis = epochMillis;
   }
 
   /**
    * @return a {@code Timestamp} representing the current time
    */
   public static Timestamp now() {
       return new Timestamp(System.currentTimeMillis());
   }
 
   /**
    * @return a {@code Timestamp} representing the given time
    */
   public static Timestamp ofEpochMilli(long epochMillis) {
       return new Timestamp(epochMillis);
   }
 
   /**
    * @return this represented as the number of milliseconds since midnight, January 1, 1970 UTC
    */
   public long toEpochMilli() {
       return mEpochMillis;
   }
 
   @Override
   public String toString() {
       return "Timestamp{epochMillis=" + mEpochMillis + "}";
   }
 
   @Override
   public boolean equals(Object other) {
       return (other instanceof Timestamp) && ((Timestamp) other).mEpochMillis == mEpochMillis;
   }
 
   @Override
   public int hashCode() {
       return Long.hashCode(mEpochMillis);
   }
}

