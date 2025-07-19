#include "org_multipaz_mdoc_zkp_longfellow_LongfellowNatives.h"

#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "privacy/proofs/zk/lib/circuits/mdoc/mdoc_zk.h"
#include "privacy/proofs/zk/lib/util/log.h"

// Helper function to convert a Java byte array to a C++ uint8_t vector
std::vector<uint8_t> jbyteArrayToUint8Vector(JNIEnv *env, jbyteArray array) {
    jbyte *arrayPtr = env->GetByteArrayElements(array, nullptr);
    jsize arrayLength = env->GetArrayLength(array);
    std::vector<uint8_t> vector(arrayPtr, arrayPtr + arrayLength);
    env->ReleaseByteArrayElements(array, arrayPtr, JNI_ABORT);
    return vector;
}

// Helper function to convert a Java string to a C++ std::string
std::string jstringToString(JNIEnv *env, jstring str) {
    const char *cStr = env->GetStringUTFChars(str, nullptr);
    std::string cppStr(cStr);
    env->ReleaseStringUTFChars(str, cStr);
    return cppStr;
}

std::vector<RequestedAttribute> parse_statement(JNIEnv *env,
                                                jobjectArray statements) {
    std::vector<RequestedAttribute> requested_attributes;
    jsize length = env->GetArrayLength(statements);

    for (jsize i = 0; i < length; ++i) {
        jobject statement = env->GetObjectArrayElement(statements, i);
        jclass statementClass = env->GetObjectClass(statement);

        // Get the key string and key size
        jfieldID keyField =
                env->GetFieldID(statementClass, "key", "Ljava/lang/String;");
        jstring keyString = (jstring) env->GetObjectField(statement, keyField);
        const char *keyChars = env->GetStringUTFChars(keyString, nullptr);
        jsize keyLength = env->GetStringUTFLength(keyString);

        // Get the value array
        jfieldID valueField = env->GetFieldID(statementClass, "value", "[B");
        jbyteArray valueArray =
                (jbyteArray) env->GetObjectField(statement, valueField);
        jsize valueLength = env->GetArrayLength(valueArray);
        jbyte* valueBytes = env->GetByteArrayElements(valueArray, nullptr);

        // Convert to uint8_t*
        std::vector<uint8_t> byteArray(valueBytes, valueBytes + valueLength);

        RequestedAttribute newRa{};

        memcpy(newRa.id, keyChars, std::min((size_t)keyLength, sizeof(newRa.id)));
        newRa.id_len = std::min((size_t)keyLength, sizeof(newRa.id));

        memcpy(newRa.value, byteArray.data(),
               std::min(byteArray.size(), sizeof(newRa.value)));
        newRa.value_len = std::min(byteArray.size(), sizeof(newRa.value));

        requested_attributes.push_back(newRa);

        env->ReleaseStringUTFChars(keyString, keyChars);
        env->ReleaseByteArrayElements(valueArray, valueBytes, JNI_ABORT);
        env->DeleteLocalRef(statement);
        env->DeleteLocalRef(keyString);
        env->DeleteLocalRef(valueArray);
        env->DeleteLocalRef(statementClass);
    }

    return requested_attributes;
}

ZkSpecStruct* getZkSpec(JNIEnv* env, jobject jzk_spec) {
    // Check if input is null
    if (jzk_spec == nullptr) {
        return nullptr;
    }

    // Find the ZkSpec class
    jclass zkSpecClass = env->GetObjectClass(jzk_spec);
    if (zkSpecClass == nullptr) {
        return nullptr;
    }

    // Get field IDs for each property
    jfieldID systemFieldId =
            env->GetFieldID(zkSpecClass, "system", "Ljava/lang/String;");
    jfieldID circuitHashFieldId =
            env->GetFieldID(zkSpecClass, "circuitHash", "Ljava/lang/String;");
    jfieldID numAttributesFieldId =
            env->GetFieldID(zkSpecClass, "numAttributes", "J");
    jfieldID versionFieldId = env->GetFieldID(zkSpecClass, "version", "J");

    // Check if any field couldn't be found
    if (!systemFieldId || !circuitHashFieldId || !numAttributesFieldId ||
        !versionFieldId) {
        env->DeleteLocalRef(zkSpecClass);
        return nullptr;
    }

    // Get the values from the Java object
    jstring systemJString = (jstring)env->GetObjectField(jzk_spec, systemFieldId);
    jstring circuitHashJString =
            (jstring)env->GetObjectField(jzk_spec, circuitHashFieldId);
    jlong numAttributes = env->GetLongField(jzk_spec, numAttributesFieldId);
    jlong version = env->GetLongField(jzk_spec, versionFieldId);

    // Convert Java strings to C strings
    const char* systemStr =
            systemJString ? env->GetStringUTFChars(systemJString, nullptr) : nullptr;
    const char* circuitHashStr = circuitHashJString
                                 ? env->GetStringUTFChars(circuitHashJString, nullptr) : nullptr;

    if (!systemStr || !circuitHashStr) {
        // Clean up if we couldn't get the strings
        if (systemStr) env->ReleaseStringUTFChars(systemJString, systemStr);
        if (circuitHashStr)
            env->ReleaseStringUTFChars(circuitHashJString, circuitHashStr);
        env->DeleteLocalRef(systemJString);
        env->DeleteLocalRef(circuitHashJString);
        env->DeleteLocalRef(zkSpecClass);
        return nullptr;
    }

    // Make a copy of the system string since we'll free the Java string
    char* systemCopy = strdup(systemStr);

    // Use placement new to initialize the struct properly
    // First allocate memory
    void* memory = malloc(sizeof(ZkSpecStruct));
    if (!memory) {
        free(systemCopy);
        env->ReleaseStringUTFChars(systemJString, systemStr);
        env->ReleaseStringUTFChars(circuitHashJString, circuitHashStr);
        env->DeleteLocalRef(systemJString);
        env->DeleteLocalRef(circuitHashJString);
        env->DeleteLocalRef(zkSpecClass);
        return nullptr;
    }

    // Use placement new with a constructor-like approach
    ZkSpecStruct* result = new(memory) ZkSpecStruct{
            systemCopy,
            {0},
            static_cast<size_t>(numAttributes),
            static_cast<size_t>(version)
    };

    // Copy the circuit hash string using memcpy for the const array
    // First zero out the array
    memset(const_cast<char*>(result->circuit_hash), 0, 65);
    // Then copy the data (up to 64 chars to leave room for null terminator)
    size_t len = strlen(circuitHashStr);
    len = (len > 64) ? 64 : len;
    memcpy(const_cast<char*>(result->circuit_hash), circuitHashStr, len);

    // Release Java strings
    env->ReleaseStringUTFChars(systemJString, systemStr);
    env->ReleaseStringUTFChars(circuitHashJString, circuitHashStr);

    // Clean up local references
    env->DeleteLocalRef(systemJString);
    env->DeleteLocalRef(circuitHashJString);
    env->DeleteLocalRef(zkSpecClass);

    return result;
}

JNIEXPORT jobject JNICALL
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_getZkSpec(
        JNIEnv *env, jclass clazz, jint numAttributes) {
    jclass zkSpecClass = env->FindClass("org/multipaz/mdoc/zkp/longfellow/LongfellowZkSystemSpec");
    if (zkSpecClass == nullptr) {
        return nullptr;
    }

    // Get ZkSpec constructor
    jmethodID zkSpecConstructor = env->GetMethodID(zkSpecClass, "<init>",
                                                   "(Ljava/lang/String;Ljava/lang/String;JJ)V");
    if (zkSpecConstructor == nullptr) {
        env->DeleteLocalRef(zkSpecClass);
        return nullptr;
    }

    for (size_t i = 0; i < kNumZkSpecs; ++i) {
        const ZkSpecStruct* zk_spec = &kZkSpecs[i];
        if (zk_spec->num_attributes == numAttributes){
            jstring systemStr = env->NewStringUTF(zk_spec->system);
            jstring circuitHashStr = env->NewStringUTF(zk_spec->circuit_hash);

            // Create ZkSpec object
            jobject zkSpecObj = env->NewObject(zkSpecClass, zkSpecConstructor,
                                               systemStr, circuitHashStr,
                                               static_cast<jlong>(zk_spec->num_attributes),
                                               static_cast<jlong>(zk_spec->version));

            env->DeleteLocalRef(systemStr);
            env->DeleteLocalRef(circuitHashStr);
            env->DeleteLocalRef(zkSpecClass);

            return zkSpecObj;
        }
    }

    env->DeleteLocalRef(zkSpecClass);
    return nullptr;
}

JNIEXPORT jbyteArray
Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_generateCircuitNative(
        JNIEnv *env, jclass clazz, jobject jzk_spec){
uint8_t* circuitBytes = nullptr;
size_t circuitBytesLen = 0;

ZkSpecStruct* zk_spec = getZkSpec(env, jzk_spec);
if (zk_spec == nullptr) {
log(proofs::ERROR, "Cannot parse ZkSpec.");
return nullptr;
}

CircuitGenerationErrorCode result =
        generate_circuit(zk_spec, &circuitBytes, &circuitBytesLen);

free((void*)zk_spec->system);
free(zk_spec);

if (result != CIRCUIT_GENERATION_SUCCESS) {
log(proofs::ERROR, "Circuit generation failed with error code: %d", result);
return nullptr;
}
jbyteArray bytesResult = env->NewByteArray(circuitBytesLen);
env->SetByteArrayRegion(bytesResult, 0, circuitBytesLen,
(jbyte*)circuitBytes);

free(circuitBytes);
return bytesResult;
}

JNIEXPORT jbyteArray JNICALL
        Java_org_multipaz_mdoc_zkp_longfellow_LongfellowNatives_runMdocProverNative(
        JNIEnv *env, jclass clazz,
jbyteArray bcp, jint bcsz,
jbyteArray mdoc, jint mdoc_len,
jstring pkx, jstring pky,
jbyteArray transcript, jint tr_len,
jstring now, jobject jzk_spec, jobjectArray statements) {
// Convert statements to RequestedAttribute array
std::vector<RequestedAttribute> requested_attributes =
        parse_statement(env, statements);

// Parse ZkSpec
ZkSpecStruct* zk_spec = getZkSpec(env, jzk_spec);
if (zk_spec == nullptr) {
log(proofs::ERROR, "Cannot parse ZkSpec.");
return nullptr;
}

// Convert Java byte arrays
std::vector<uint8_t> bcp_vector = jbyteArrayToUint8Vector(env, bcp);
std::vector<uint8_t> mdoc_vector = jbyteArrayToUint8Vector(env, mdoc);
std::vector<uint8_t> transcript_vector =
        jbyteArrayToUint8Vector(env, transcript);

// Convert Java strings to strings
std::string public_key_x = jstringToString(env, pkx);
std::string public_key_y = jstringToString(env, pky);
std::string now_str = jstringToString(env, now);

// Prepare output variables
size_t proof_len_out;
uint8_t *proof_out;

// Generate the ZK proof
MdocProverErrorCode result = run_mdoc_prover(
        (const uint8_t *) bcp_vector.data(), bcsz,
        (const uint8_t *) mdoc_vector.data(), mdoc_len,
        public_key_x.c_str(), public_key_y.c_str(),
        (const uint8_t *) transcript_vector.data(), tr_len,
        requested_attributes.data(), requested_attributes.size(),
        now_str.c_str(),
        (uint8_t **) &proof_out, &proof_len_out, zk_spec);

if (result != MDOC_PROVER_SUCCESS) {
jclass proofGenExceptionClass =
        env->FindClass("org/multipaz/mdoc/zkp/longfellow/ProofGenerationException");
std::string error_message =
        "Proof generation failed with error code: " + std::to_string(result);
env->ThrowNew(proofGenExceptionClass, error_message.c_str());
return nullptr;
}

jbyteArray bytesResult = env->NewByteArray(proof_len_out);
env->SetByteArrayRegion(bytesResult, 0, proof_len_out, (jbyte*)proof_out);
free(proof_out);
return bytesResult;
}

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
        jobjectArray statements) {
// Convert statements to RequestedAttribute array
std::vector<RequestedAttribute> requested_attributes =
        parse_statement(env, statements);

// Parse ZkSpec
ZkSpecStruct* zk_spec = getZkSpec(env, jzk_spec);
if (zk_spec == nullptr) {
log(proofs::ERROR, "Cannot parse ZkSpec.");
return MdocVerifierErrorCode::MDOC_VERIFIER_INVALID_INPUT;
}

// Convert Java byte arrays
std::vector<uint8_t> bcp_vector = jbyteArrayToUint8Vector(env, bcp);
std::vector<uint8_t> zk_proof = jbyteArrayToUint8Vector(env, zkproof);
std::vector<uint8_t> transcript_vector =
        jbyteArrayToUint8Vector(env, transcript);

// Convert Java strings to strings
std::string public_key_x = jstringToString(env, pkx);
std::string public_key_y = jstringToString(env, pky);
std::string doc_type = jstringToString(env, docType);
std::string now_str = jstringToString(env, now);

// Call the C function
MdocVerifierErrorCode result = run_mdoc_verifier(
        (const uint8_t *) bcp_vector.data(), bcsz,
        public_key_x.c_str(), public_key_y.c_str(),
        (const uint8_t *) transcript_vector.data(), tr_len,
        requested_attributes.data(), requested_attributes.size(),
        now_str.c_str(),
        (const uint8_t *) zk_proof.data(), proof_len,
        doc_type.c_str(), zk_spec);

return static_cast<jint>(result);
}
