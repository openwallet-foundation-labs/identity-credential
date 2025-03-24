package com.licel.jcardsim.utils;

/**
 * Back-port of Java 8 <code>java.util.function.BiConsumer</code>.
 */
public interface BiConsumer<T,U> {
    void accept(T t, U u);
}
