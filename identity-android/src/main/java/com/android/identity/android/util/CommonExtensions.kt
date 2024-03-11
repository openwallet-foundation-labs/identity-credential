package com.android.identity.android.util

/**
 * Extension function to prettify if-true flag evaluation logic, such as
 *
 * return if (booleanValue){
 *      // do work
 *      } else {
 *      // do nothing
 *      }
 *
 * to
 *
 * return booleanValue.ifTrue {
 *      // do work
 * }
 *
 * @param block function to run if the boolean value is true
 */
fun Boolean.ifTrue(block: () -> Unit) =
    if (this)
        block.invoke()
    else { // do nothing
    }

/**
 * Extension function to prettify if-false flag evaluation logic, such as
 *
 * return if (!booleanValue){
 *          // do work
 *      } else {
 *          // do nothing
 *      }
 *
 * to
 *
 * return booleanValue.ifFalse {
 *      // do work
 * }
 * @param block function to run if the boolean value is false
 */
fun Boolean.ifFalse(block: () -> Unit) = (!this).ifTrue(block)
