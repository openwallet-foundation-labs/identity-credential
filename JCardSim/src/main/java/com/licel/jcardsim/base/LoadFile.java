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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Represents a LoadFile (a JavaCard package or library).
 */
public final class LoadFile {
    private final AID aid;
    private final List<Module> modules;

    /**
     * Create a LoadFile containing <code>1..*</code> modules (JavaCard applets)
     * @param loadFileAID AID of the LoadFile (JavaCard Package AID)
     * @param modules array of modules
     * @throws java.lang.NullPointerException if any argument is null
     * @throws java.lang.IllegalArgumentException if <code>modules</code> is empty
     */
    public LoadFile(AID loadFileAID, Module... modules) {
        if (loadFileAID == null) {
            throw new NullPointerException("loadFileAID");
        }
        if (modules == null) {
            throw new NullPointerException("modules");
        }
        if (modules.length == 0) {
            throw new IllegalArgumentException("modules must not be empty");
        }

        this.aid = loadFileAID;
        this.modules = Collections.unmodifiableList(Arrays.asList(modules));
    }

    /**
     * Create a LoadFile containing one module (JavaCard applet)
     * @param loadFileAID AID of the LoadFile (JavaCard Package AID)
     * @param moduleAid AID of the module/class
     * @param appletClass the Applet class
     * @throws java.lang.NullPointerException if any argument is null
     * @throws java.lang.IllegalArgumentException if <code>modules</code> is empty
     */
    public LoadFile(AID loadFileAID, AID moduleAid, Class<? extends Applet> appletClass) {
        this(loadFileAID, new Module(moduleAid, appletClass));
    }

    public AID getAid() {
        return aid;
    }

    public List<Module> getModules() {
        return modules;
    }

    public Module getModule(AID moduleAID) {
        for (Module module : modules) {
            if (module.getAid().equals(moduleAID)) {
                return module;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("LoadFile ").append(AIDUtil.toString(aid)).append("\n");
        for (Module module : modules) {
            stringBuilder.append("  ").append(module.toString()).append("\n");
        }
        return stringBuilder.toString();
    }
}
