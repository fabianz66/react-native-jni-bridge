package com.reactnativejnibridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

/**
 * JniBridgeModule is a React Native native module that exposes a C++ function
 * to JavaScript through two layers: the RN bridge and the Java Native Interface (JNI).
 *
 * WHY IT EXISTS
 * JavaScript cannot call C++ directly. To reach native C++ code from JS you need
 * two hops:
 *   1. JS → Kotlin  via the React Native native module system (@ReactMethod)
 *   2. Kotlin → C++ via JNI                                  (external fun)
 *
 * This class provides the first hop. It acts as a typed adapter between the
 * dynamically-typed JS world and the statically-typed native world.
 *
 * HOW THE RN SIDE WORKS
 * Extending [ReactContextBaseJavaModule] and overriding [getName] is all that is
 * needed to make this class discoverable. React Native reads the name at startup
 * (via [MyPackage]) and registers it in the TurboModule registry under "JniBridge".
 * From JS, this becomes accessible as:
 *   NativeModules.JniBridge.getString()  →  Promise<string>
 *
 * Every public method annotated with [@ReactMethod] is callable from JS. Methods
 * must be async — they receive a [Promise] to signal completion because the JS
 * thread must never block waiting for a native result. The method body runs on
 * the native modules thread (a background thread), and promise.resolve() /
 * promise.reject() schedule the callback back onto the JS thread via JSI.
 *
 * HOW THE JNI SIDE WORKS
 * The companion object's `init` block runs [System.loadLibrary] exactly once when
 * this class is first loaded by the JVM. This loads libjnibridge.so (built from
 * cpp/jni_bridge.cpp by CMake) into the process. After loading, the JVM resolves
 * the `external fun nativeGetString()` declaration by looking up the C symbol:
 *
 *   Java_com_reactnativejnibridge_JniBridgeModule_nativeGetString
 *
 * That symbol is defined in jni_bridge.cpp with the matching JNIEXPORT signature.
 * From that point on, calling `nativeGetString()` in Kotlin is a direct native
 * function call — no reflection, no serialisation.
 *
 * WHY THE METHOD IS PRIVATE
 * [nativeGetString] is marked private because it is an implementation detail.
 * Only [getString] (the @ReactMethod) should call it, and it wraps the call in
 * a try/catch so that any JNI-level crash (e.g. UnsatisfiedLinkError if the
 * library was not loaded) is surfaced to JS as a rejected Promise rather than
 * an unhandled native exception that would crash the app.
 */
class JniBridgeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        init {
            // Loads libjnibridge.so into the process. Runs once at class-load time,
            // before any instance of this module is created. If the library is missing
            // or has unresolved symbols, this throws UnsatisfiedLinkError.
            System.loadLibrary("jnibridge")
        }
    }

    // The string returned here is the key used on the JS side:
    // NativeModules.JniBridge → this module.
    override fun getName() = "JniBridge"

    // Declares a native method whose body is implemented in C++.
    // The JVM links this to Java_com_reactnativejnibridge_JniBridgeModule_nativeGetString
    // inside libjnibridge.so at load time.
    private external fun nativeGetString(): String

    /**
     * Callable from JS as: await NativeModules.JniBridge.getString()
     *
     * Runs on the native modules thread. Calls into C++ via JNI and resolves
     * the promise with the returned string. Any exception (including native
     * crashes surfaced as Java exceptions) is caught and forwarded as a
     * JS-visible rejection so the caller can handle it gracefully.
     */
    @ReactMethod
    fun getString(promise: Promise) {
        try {
            promise.resolve(nativeGetString())
        } catch (e: Exception) {
            promise.reject("JNI_ERROR", e.message, e)
        }
    }
}
