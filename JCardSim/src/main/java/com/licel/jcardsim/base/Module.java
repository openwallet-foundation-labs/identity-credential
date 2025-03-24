/*
 * Copyright 2014 Robert Bachmann
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
package com.licel.jcardsim.base;

import com.licel.jcardsim.utils.AIDUtil;
import javacard.framework.AID;
import javacard.framework.Applet;

/**
 * Represents a module (JavaCard Applet class).
 */
public final class Module {
    private final AID aid;
    private final Class<? extends Applet> appletClass;

    /**
     * Create a module
     * @param moduleAID AID of the applet class
     * @param appletClass Applet class
     */
    public Module(AID moduleAID, Class<? extends Applet> appletClass) {
        this.aid = moduleAID;
        this.appletClass = appletClass;
        if (moduleAID == null) {
            throw new NullPointerException("moduleAID");
        }
        if (appletClass == null) {
            throw new NullPointerException("appletClass");
        }
        if (!Applet.class.isAssignableFrom(appletClass)) {
            throw new IllegalArgumentException("appletClass " + appletClass + " must derive from Applet.");
        }
    }

    public AID getAid() {
        return aid;
    }

    public Class<? extends Applet> getAppletClass() {
        return appletClass;
    }

    @Override
    public String toString() {
        return String.format("Module %s (%s)", AIDUtil.toString(aid), appletClass.getCanonicalName());
    }
}
