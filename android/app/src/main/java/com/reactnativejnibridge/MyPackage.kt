package com.reactnativejnibridge

import com.facebook.react.ReactPackage
import com.facebook.react.bridge.ReactApplicationContext

class MyPackage : ReactPackage {
    override fun createNativeModules(context: ReactApplicationContext) =
        listOf(JniBridgeModule(context))

    override fun createViewManagers(context: ReactApplicationContext) =
        listOf(VideoPlayerViewManager())
}
