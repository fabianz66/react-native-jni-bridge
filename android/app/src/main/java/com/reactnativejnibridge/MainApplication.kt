package com.reactnativejnibridge

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost

/**
 * MainApplication is the Android Application subclass and the top-level entry point
 * for the entire React Native runtime.
 *
 * WHY IT EXISTS
 * Every Android app has exactly one Application instance, created before any Activity.
 * React Native requires a custom Application class so it can initialise its runtime
 * (the JS engine, the native module registry, the Fabric renderer) before the first
 * screen appears. Without this class the app would have no JS engine to execute
 * bundle code and no bridge to reach native modules.
 *
 * WHAT IT DOES
 * 1. Implements [ReactApplication] so the rest of the RN framework can call
 *    [reactHost] to reach the shared runtime object from anywhere in the process.
 *
 * 2. Builds a [ReactHost] via [getDefaultReactHost]. ReactHost (New Architecture)
 *    replaces the older ReactInstanceManager. It owns:
 *      - The Hermes JS engine instance
 *      - The JS bundle loader
 *      - The complete list of native packages (modules + view managers)
 *      - The Fabric surface manager
 *    It is created lazily so the heavy initialisation only happens when the first
 *    React surface actually needs to render.
 *
 * 3. Merges packages: [PackageList] collects every library that declared itself
 *    via autolinking (react-native-screens, react-native-safe-area-context, …).
 *    We then add [MyPackage] manually because it is app-level code — autolinking
 *    only works for third-party libraries published as npm packages with a
 *    react-native.config.js descriptor.
 *
 * 4. Calls [loadReactNative] in [onCreate], which loads the pre-built native
 *    shared libraries (libreactnative.so, libhermes.so, libappmodules.so) into
 *    the process via SoLoader before any JS executes.
 *
 * NEW ARCHITECTURE NOTE
 * In the Old Architecture this class held a ReactInstanceManager and called
 * ReactNativeHost.getReactInstanceManager(). In the New Architecture (0.76+)
 * those types are replaced by ReactHost, which drives Fabric and TurboModules.
 * The [getDefaultReactHost] helper wires all of that up with sensible defaults.
 */
class MainApplication : Application(), ReactApplication {

  override val reactHost: ReactHost by lazy {
    getDefaultReactHost(
      context = applicationContext,
      packageList =
        PackageList(this).packages.apply {
          // MyPackage is not autolinked because it lives inside the app module,
          // not in a standalone npm package. It must be added here explicitly.
          add(MyPackage())
        },
    )
  }

  override fun onCreate() {
    super.onCreate()
    // Loads all React Native .so libraries into memory. Must be called before
    // any Activity tries to create a React surface.
    loadReactNative(this)
  }
}
