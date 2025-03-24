/*
 * Copyright 2011 Licel LLC.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javacard.framework.JCSystem;
import javacard.framework.SystemException;

/**
 * Basic implementation of storage transient memory of JCRE.
 */
public class TransientMemory {
    /** List of <code>CLEAR_ON_DESELECT</code> arrays */
    protected final ArrayList<Object> clearOnDeselect = new ArrayList<Object>();
    /** List of <code>CLEAR_ON_RESET</code> arrays */
    protected final ArrayList<Object> clearOnReset = new ArrayList<Object>();

    /**
     * @see javacard.framework.JCSystem#makeTransientBooleanArray(short, byte)
     * @param length the length of the array
     * @param event the <code>CLEAR_ON...</code> event which causes the array elements to be cleared
     * @return the new transient array
     */
    public boolean[] makeBooleanArray(short length, byte event) {
        boolean[] array = new boolean[length];
        storeArray(array, event);
        return array;
    }

    /**
     * @see javacard.framework.JCSystem#makeTransientByteArray(short, byte)
     * @param length the length of the array
     * @param event the <code>CLEAR_ON...</code> event which causes the array elements to be cleared
     * @return the new transient array
     */
    public byte[] makeByteArray(int length, byte event) {
        byte[] array = new byte[length];
        storeArray(array, event);
        return array;
    }

    /**
     * @see javacard.framework.JCSystem#makeTransientShortArray(short, byte)
     * @param length the length of the array
     * @param event the <code>CLEAR_ON...</code> event which causes the array elements to be cleared
     * @return the new transient array
     */
    public short[] makeShortArray(short length, byte event) {
        short[] array = new short[length];
        storeArray(array, event);
        return array;
    }

    /**
     * @see javacard.framework.JCSystem#makeTransientObjectArray(short, byte)
     * @param length the length of the array
     * @param event the <code>CLEAR_ON...</code> event which causes the array elements to be cleared
     * @return the new transient array
     */
    public Object[] makeObjectArray(short length, byte event) {
        Object[] array = new Object[length];
        storeArray(array, event);
        return array;
    }

    /**
     * @see javacard.framework.JCSystem#makeGlobalArray(byte,short)
     * @param type the array type - must be one of : ARRAY_TYPE_BOOLEAN, ARRAY_TYPE_BYTE, ARRAY_TYPE_SHORT, ARRAY_TYPE_INT, or ARRAY_TYPE_OBJECT
     * @param length the length of the global transient array
     * @return the new transient Object array
     */
    public Object makeGlobalArray(byte type, short length){
        Object array = null;
        switch (type){
            case JCSystem.ARRAY_TYPE_BOOLEAN:
                array = makeBooleanArray(length, JCSystem.CLEAR_ON_RESET);
                break;
            case JCSystem.ARRAY_TYPE_BYTE:
                array = makeByteArray(length, JCSystem.CLEAR_ON_RESET);
                break;
            case JCSystem.ARRAY_TYPE_SHORT:
                array = makeShortArray(length, JCSystem.CLEAR_ON_RESET);
                break;
            case JCSystem.ARRAY_TYPE_OBJECT:
                array = makeObjectArray(length, JCSystem.CLEAR_ON_RESET);
                break;
            case JCSystem.ARRAY_TYPE_INT:
            default:
                SystemException.throwIt(SystemException.ILLEGAL_VALUE);
                break;
        }

        return array;
    }

    /**
     * @see javacard.framework.JCSystem#isTransient(Object)
     * @param theObj the object being queried
     * @return <code>NOT_A_TRANSIENT_OBJECT</code>, <code>CLEAR_ON_RESET</code>, or <code>CLEAR_ON_DESELECT</code>
     */
    public byte isTransient(Object theObj) {
        if (clearOnDeselect.contains(theObj)) {
            return JCSystem.CLEAR_ON_DESELECT;
        } else if (clearOnReset.contains(theObj)) {
            return JCSystem.CLEAR_ON_RESET;
        }
        return JCSystem.NOT_A_TRANSIENT_OBJECT;
    }

    /**
     * Store <code>arrayRef</code> in memory depends by event type
     * @param arrayRef array reference
     * @param event event type
     */
    protected void storeArray(Object arrayRef, byte event) {
        switch (event) {
            case JCSystem.CLEAR_ON_DESELECT:
                clearOnDeselect.add(arrayRef);
                break;
            case JCSystem.CLEAR_ON_RESET:
                clearOnReset.add(arrayRef);
                break;
            default:
                SystemException.throwIt(SystemException.ILLEGAL_VALUE);
        }
    }

    /**
     * Zero <code>CLEAR_ON_DESELECT</code> buffers
     */
    protected void clearOnDeselect() {
        zero(clearOnDeselect);
    }

    /**
     * Zero <code>CLEAR_ON_RESET</code> and <code>CLEAR_ON_DESELECT</code>
     * buffers
     */
    protected void clearOnReset() {
        zero(clearOnDeselect);
        zero(clearOnReset);
    }

    /**
     * Perform <code>clearOnReset</code> and forget all buffers
     */
    protected void forgetBuffers() {
        clearOnReset();
        clearOnDeselect.clear();
        clearOnReset.clear();
    }

    /**
     * Zero all arrays in list
     * @param list list of arrays
     */
    protected void zero(List<Object> list) {
        for (Object obj : list) {
            if (obj instanceof byte[]) {
                Arrays.fill((byte[]) obj, (byte) 0);
            }
            else if (obj instanceof short[]) {
                Arrays.fill((short[]) obj, (short) 0);
            }
            else if (obj instanceof Object[]) {
                Arrays.fill((Object[])obj, null);
            }
            else if (obj instanceof boolean[]) {
                boolean[] array = (boolean[]) obj;
                for (int i = 0; i < array.length; ++i) {
                    array[i] = false;
                }
            }
        }
    }
}
