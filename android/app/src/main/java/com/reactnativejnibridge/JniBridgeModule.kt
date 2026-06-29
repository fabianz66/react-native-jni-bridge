package com.reactnativejnibridge

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod

class JniBridgeModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        init {
            System.loadLibrary("jnibridge")
        }
    }

    override fun getName() = "JniBridge"

    private external fun nativeGetString(): String

    @ReactMethod
    fun getString(promise: Promise) {
        try {
            promise.resolve(nativeGetString())
        } catch (e: Exception) {
            promise.reject("JNI_ERROR", e.message, e)
        }
    }
}
