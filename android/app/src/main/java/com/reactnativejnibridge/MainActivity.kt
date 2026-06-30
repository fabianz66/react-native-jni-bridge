package com.reactnativejnibridge

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate

/**
 * MainActivity is the single Android Activity that hosts the entire React Native UI.
 *
 * WHY IT EXISTS
 * Android requires at least one Activity as the user-visible entry point declared
 * in AndroidManifest.xml with an MAIN/LAUNCHER intent filter. React Native renders
 * all of its UI inside one Activity — navigation between "screens" (Home, Player)
 * is handled entirely in JS by React Navigation; no new Android Activities are
 * created when the user taps Play.
 *
 * WHAT IT DOES
 * Extends [ReactActivity], which takes care of:
 *   - Creating a ReactRootView and attaching it as the content view
 *   - Starting the JS bundle load (from Metro in debug, from assets in release)
 *   - Forwarding Android lifecycle events (onPause, onResume, onBackPressed, …)
 *     to the JS layer so React components can react to them
 *   - Handling dev-menu gestures (shake) in debug builds
 *
 * [getMainComponentName] returns the string passed to AppRegistry.registerComponent()
 * in index.js. React Native uses it to find the root JS component to render inside
 * this Activity's window.
 *
 * [createReactActivityDelegate] returns a [DefaultReactActivityDelegate] configured
 * with [fabricEnabled]. Fabric is React Native's New Architecture renderer — when
 * true, the Activity sets up the Fabric surface instead of the legacy Paper renderer.
 * The [fabricEnabled] flag comes from gradle.properties (newArchEnabled=true).
 *
 * THREADING NOTE
 * This Activity runs on the Android main thread. The JS thread and the native
 * modules thread are separate threads managed by the React Native runtime.
 * UI mutations from the JS side are marshalled back to the main thread by Fabric
 * before being applied to the Android view hierarchy.
 */
class MainActivity : ReactActivity() {

  /**
   * Returns the name of the main component registered from JavaScript. This is used to schedule
   * rendering of the component.
   */
  override fun getMainComponentName(): String = "ReactNativeJniBridge"

  /**
   * Returns the instance of the [ReactActivityDelegate]. We use [DefaultReactActivityDelegate]
   * which allows you to enable New Architecture with a single boolean flags [fabricEnabled]
   */
  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)
}
