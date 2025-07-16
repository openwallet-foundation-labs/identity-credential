#include <jni.h>
#include <jni_md.h>

#ifndef LONGFELLOW_LONGFELLOWNATIVES_H_
#define LONGFELLOW_LONGFELLOWNATIVES_H_

extern "C" {
JNIEXPORT jobject JNICALL
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_getZkSpec(JNIEnv *env, jclass clazz, jint numAttributes);

JNIEXPORT jbyteArray JNICALL
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_generateCircuitNative(
        JNIEnv *env, jclass clazz, jobject zk_spec);

JNIEXPORT jbyteArray JNICALL
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_runMdocProverNative(
        JNIEnv *env, jclass clazz,
        jbyteArray bcp, jint bcsz,
        jbyteArray mdoc, jint mdoc_len,
        jstring pkx, jstring pky,
        jbyteArray transcript, jint tr_len,
        jstring now, jobject jzk_spec, jobjectArray statements);

JNIEXPORT jint JNICALL
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_runMdocVerifierNative(
        JNIEnv *env, jclass clazz,
        jbyteArray bcp, jint bcsz,
        jstring pkx, jstring pky,
        jbyteArray transcript, jint tr_len,
        jstring now,
        jbyteArray zkproof, jint proof_len,
        jstring docType,
        jobject jzk_spec,
        jobjectArray statements);

}
#endif  // LONGFELLOW_LONGFELLOWNATIVES_H_
