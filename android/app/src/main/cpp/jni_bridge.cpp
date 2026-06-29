#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_reactnativejnibridge_JniBridgeModule_nativeGetString(JNIEnv* env, jobject /* thiz */) {
    std::string msg = "Hello from C++ via JNI!";
    return env->NewStringUTF(msg.c_str());
}
