# ReactNativeJniBridge

A React Native 0.86 app (Android) that demonstrates three things working together:

1. A **home screen** with a URL input and Play button
2. A **native ExoPlayer** video player rendered as a React Native view component
3. A **JNI bridge** that calls a C++ function from JavaScript via Kotlin

---

## Architecture Overview

```
JavaScript (React Native)
        │
        ├── Navigation (React Navigation / native-stack)
        │       ├── HomeScreen     — URL input + Play button
        │       └── PlayerScreen   — ExoPlayer view + JNI string
        │
        ├── VideoPlayer component  — requireNativeComponent('VideoPlayer')
        │       └── VideoPlayerViewManager.kt  (SimpleViewManager<PlayerView>)
        │               └── ExoPlayer (Media3)  — plays HLS / HTTP streams
        │
        └── NativeModules.JniBridge.getString()
                └── JniBridgeModule.kt  (ReactContextBaseJavaModule)
                        └── System.loadLibrary("jnibridge")
                                └── libjnibridge.so  (C++)
                                        └── Java_..._nativeGetString()
                                                └── returns "Hello from C++ via JNI!"
```

---

## Project Structure

```
ReactNativeJniBridge/
├── App.tsx                            # Navigation container + stack definition
├── src/
│   ├── screens/
│   │   ├── HomeScreen.tsx             # URL TextInput + Play button
│   │   └── PlayerScreen.tsx           # Hosts VideoPlayer view + JNI label
│   └── components/
│       └── VideoPlayer.tsx            # requireNativeComponent wrapper
└── android/
    └── app/src/main/
        ├── jni/
        │   └── CMakeLists.txt         # Master CMake — builds libappmodules.so + libjnibridge.so
        ├── cpp/
        │   └── jni_bridge.cpp         # C++ source for libjnibridge.so
        └── java/com/reactnativejnibridge/
            ├── MainApplication.kt     # Registers MyPackage
            ├── MainActivity.kt        # Entry point (unchanged)
            ├── MyPackage.kt           # ReactPackage that exposes both native pieces
            ├── JniBridgeModule.kt     # Native module (calls C++ via JNI)
            └── VideoPlayerViewManager.kt  # ViewManager that wraps ExoPlayer's PlayerView
```

---

## JavaScript Layer

### `App.tsx` — Navigation root

Sets up a `NativeStackNavigator` with two screens. The dark header style is configured here so it applies globally.

```
Stack
 ├── Home   →  HomeScreen
 └── Player →  PlayerScreen  (receives { url: string } as route param)
```

### `HomeScreen.tsx`

A controlled `TextInput` (default `http://10.0.0.2/master.m3u8`) and a `TouchableOpacity` that calls `navigation.navigate('Player', { url })`.

### `PlayerScreen.tsx`

Renders two things:
- `<VideoPlayer style={{ flex: 1 }} url={url} />` — the native ExoPlayer view
- A bottom panel that calls `NativeModules.JniBridge.getString()` on mount and displays the result

### `src/components/VideoPlayer.tsx`

```ts
export default requireNativeComponent<{ url: string; style?: ViewStyle }>('VideoPlayer');
```

`requireNativeComponent` tells React Native to look up a registered `ViewManager` named `"VideoPlayer"` on the native side and treat it as a React component. Props are forwarded to the native view via the bridge.

---

## Android Native Layer

### `MyPackage.kt` — ReactPackage

The entry point for all custom native code. A `ReactPackage` is a container that groups native modules and view managers so React Native can discover them.

```kotlin
class MyPackage : ReactPackage {
    override fun createNativeModules(context) = listOf(JniBridgeModule(context))
    override fun createViewManagers(context) = listOf(VideoPlayerViewManager())
}
```

Registered in `MainApplication.kt`:
```kotlin
PackageList(this).packages.apply { add(MyPackage()) }
```

### `VideoPlayerViewManager.kt` — ExoPlayer as a native view

`SimpleViewManager<PlayerView>` is the React Native API for wrapping an Android `View` subclass as a component. The lifecycle is:

| Method | When it runs | What it does |
|---|---|---|
| `createViewInstance()` | First render | Creates `ExoPlayer` + `PlayerView`, wires them together |
| `@ReactProp("url") setUrl()` | Whenever the `url` prop changes | Loads the media item, calls `prepare()` and `playWhenReady = true` |
| `onDropViewInstance()` | Component unmounts | Calls `player.release()` to free codec and surface resources |

`PlayerView` is from `androidx.media3:media3-ui`. It renders the video surface and playback controls. The underlying decoder is `ExoPlayer` from `androidx.media3:media3-exoplayer`. HLS streams (`.m3u8`) are handled automatically by `androidx.media3:media3-exoplayer-hls` — ExoPlayer detects the format from the URI with no extra configuration.

### `JniBridgeModule.kt` — Native module with JNI

A `ReactContextBaseJavaModule` exposes methods to JavaScript via `@ReactMethod`. This module also loads a native shared library and declares an `external` function (Kotlin's keyword for JNI-implemented functions).

```kotlin
companion object {
    init { System.loadLibrary("jnibridge") }  // loads libjnibridge.so at class-load time
}

private external fun nativeGetString(): String  // body is in C++

@ReactMethod
fun getString(promise: Promise) {
    promise.resolve(nativeGetString())
}
```

When JS calls `NativeModules.JniBridge.getString()`, it returns a `Promise` that resolves with whatever the C++ function returns.

---

## C++ / JNI Layer

### `cpp/jni_bridge.cpp`

```cpp
extern "C" JNIEXPORT jstring JNICALL
Java_com_reactnativejnibridge_JniBridgeModule_nativeGetString(JNIEnv* env, jobject) {
    return env->NewStringUTF("Hello from C++ via JNI!");
}
```

The function name follows the JNI naming convention exactly:

```
Java_<package_with_underscores>_<ClassName>_<methodName>
  └─► Java_com_reactnativejnibridge_JniBridgeModule_nativeGetString
```

When `System.loadLibrary("jnibridge")` runs, the JVM links the `external fun nativeGetString()` declaration in Kotlin to this C symbol at runtime. Calling the Kotlin function dispatches directly into native code.

---

## Build System — The Critical Detail

### Why `src/main/jni/CMakeLists.txt` (not `src/main/cpp/`)?

React Native 0.86 with New Architecture (`newArchEnabled=true`) requires a shared library called **`libappmodules.so`** at startup. This library is the TurboModule registry — the lookup table that maps module names like `"PlatformConstants"` and `"JniBridge"` to their C++ or Java implementations.

`libappmodules.so` is generated by the React Native Gradle Plugin via CMake. The plugin injects three variables into the cmake build:

| Variable | Value |
|---|---|
| `REACT_ANDROID_DIR` | `node_modules/react-native/ReactAndroid` |
| `PROJECT_BUILD_DIR` | Gradle build output directory |
| `PROJECT_ROOT_DIR` | Root of the Android project |

The plugin expects to control the `externalNativeBuild` cmake path in `build.gradle`. If you add your own standalone `CMakeLists.txt` without including RN's utilities, `libappmodules.so` is never built, and the app crashes on launch with:

```
Invariant Violation: TurboModuleRegistry.getEnforcing('PlatformConstants') could not be found
```

The fix is to place `CMakeLists.txt` in `src/main/jni/` and **include RN's cmake utilities first**, so both libraries are built in a single cmake invocation:

```cmake
# android/app/src/main/jni/CMakeLists.txt

cmake_minimum_required(VERSION 3.22.1)
project("appmodules")   # ← must match — SoLoader looks for libappmodules.so

# Builds libappmodules.so using RN's default OnLoad.cpp + autolinking generated files.
# REACT_ANDROID_DIR, PROJECT_BUILD_DIR, PROJECT_ROOT_DIR are injected by the Gradle plugin.
include("${REACT_ANDROID_DIR}/cmake-utils/ReactNative-application.cmake")

# Our library is added alongside without interfering with libappmodules.so.
add_library(jnibridge SHARED ${CMAKE_CURRENT_SOURCE_DIR}/../cpp/jni_bridge.cpp)
find_library(log-lib log)
target_link_libraries(jnibridge ${log-lib})
```

`ReactNative-application.cmake` uses those injected variables to:
- Compile `default-app-setup/OnLoad.cpp` (the default TurboModule provider entry point)
- Pull in the autolinking-generated `.cpp` files for third-party native libraries
- Link against `fbjni`, `jsi`, and `reactnative` prefab targets from the Maven AAR

The result is two `.so` files in the APK per ABI:

| Library | Purpose |
|---|---|
| `libappmodules.so` | RN TurboModule registry — required for the JS runtime |
| `libjnibridge.so` | Our custom C++ code |

### `app/build.gradle` additions

```gradle
defaultConfig {
    externalNativeBuild {
        cmake { cppFlags "-std=c++17" }
    }
}
externalNativeBuild {
    cmake {
        path "src/main/jni/CMakeLists.txt"
        version "3.22.1"
    }
}

// Permit cleartext HTTP in debug builds (needed for http:// stream URLs)
buildTypes {
    debug {
        manifestPlaceholders = [usesCleartextTraffic: true]
    }
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
}
```

---

## JS ↔ Android Bridge — How They Communicate

There are two separate communication channels in this app, one for **method calls** (`JniBridge`) and one for **UI/views** (`VideoPlayer`). They work differently.

### Channel 1 — Native Modules (JniBridge)

This handles JS calling Kotlin functions and getting results back.

```
JS                          C++ (JSI layer)              Kotlin/Java
──                          ───────────────              ───────────
NativeModules
 .JniBridge
 .getString()
     │
     ▼
TurboModuleRegistry
 .getEnforcing('JniBridge')
     │
     │  looks up in libappmodules.so
     │  finds JniBridgeModule
     ▼
 calls getString(promise)   ──────────────────────────►  JniBridgeModule.kt
                                                              │
                                                              ▼
                                                         nativeGetString()
                                                              │  (JNI call)
                                                              ▼
                                                         jni_bridge.cpp
                                                              │
                                                         "Hello from C++ via JNI!"
                                                              │
                                                    ◄─────────┘
                             promise.resolve(string)
     │
     ▼
 .then(s => setJniString(s))
```

**What actually moves across the boundary:**

When JS calls `NativeModules.JniBridge.getString()`, the call goes through **JSI (JavaScript Interface)** — a C++ layer that gives the JS engine a direct handle to native objects. In New Architecture, native modules are exposed as C++ host objects that JS can call directly without serialization to JSON. The return value (`promise.resolve(string)`) marshals a `jstring` from JNI → a Java `String` → a JSI `jsi::String` back into the JS heap.

**Threading:** `@ReactMethod` runs on the **native modules thread** (a background thread), not the JS thread and not the UI thread. That is why the result is returned via a `Promise` rather than synchronously — the JS thread never blocks.

---

### Channel 2 — Native-to-JS Events (Player State)

This handles the native side pushing unsolicited updates to JS — the reverse direction.

```
ExoPlayer                   Kotlin (UI thread)           JS (React thread)
─────────                   ──────────────────           ─────────────────
Player.Listener
 onPlaybackStateChanged()
 onIsPlayingChanged()
 onPlayerError()
     │
     ▼
 resolveState(player)       ──► "buffering" / "playing" / "paused" / "ended" / "error"
                                     │
                                     ▼
                            DeviceEventManagerModule
                             .RCTDeviceEventEmitter
                             .emit("onPlayerStateChanged", { state, error? })
                                     │
                                     │  through JSI event queue
                                     ▼
                                            DeviceEventEmitter
                                             .addListener(
                                               'onPlayerStateChanged',
                                               e => setPlayerState(e.state)
                                             )
```

**How it works:**
- `VideoPlayerViewManager` attaches a `Player.Listener` to the ExoPlayer instance inside `createViewInstance`. The listener fires on the Android UI thread whenever ExoPlayer transitions between states.
- To decide the label, `resolveState()` reads both `player.playbackState` (IDLE / BUFFERING / READY / ENDED) and `player.isPlaying` (true only when frames are actually being rendered), collapsing them into one of: `idle`, `buffering`, `playing`, `paused`, `ended`, or `error`.
- The Kotlin side emits via `context.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java).emit(...)`. This call is thread-safe — RN schedules the delivery onto the JS thread automatically.
- The JS side subscribes with `DeviceEventEmitter.addListener('onPlayerStateChanged', callback)` inside a `useEffect` and removes the subscription on unmount to avoid stale listeners after navigation.

This pattern — **native emits, JS subscribes** — is the standard mechanism for any push-style event: playback state, sensor readings, push notifications, bluetooth events, etc.

---

### Channel 3 — View Managers (VideoPlayer)

This handles JS rendering a native Android view and sending props to it.

```
JS                          Fabric (C++ renderer)        Kotlin/Java
──                          ─────────────────────        ───────────
<VideoPlayer
  url="http://..."
  style={{ flex:1 }}
/>
  │
  ▼
React reconciler
  │  creates a shadow node
  ▼
Fabric C++ layout engine
  │  measures + positions the view
  │
  │  calls into ViewManager registry
  │  looks up "VideoPlayer"
  ▼
VideoPlayerViewManager.kt
  │
  ├── createViewInstance()    ←── called once on first render
  │       creates ExoPlayer
  │       creates PlayerView
  │       wires them together
  │
  └── setUrl(view, url)       ←── called when `url` prop changes
          player.setMediaItem(...)
          player.prepare()
          player.playWhenReady = true
```

**What actually moves across the boundary:**

Props (like `url`) are diffed by the React reconciler. Only changed props are sent. They are passed as typed values through Fabric's C++ layer into `@ReactProp`-annotated Kotlin methods. There is no JSON serialization — Fabric passes values directly as C++ primitives that map to JNI types.

The **view itself** (the `PlayerView` / `SurfaceView` that ExoPlayer draws to) lives entirely in Android's view hierarchy — it never crosses the bridge. JS only knows about it as a virtual node in React's shadow tree. Fabric reconciles the shadow tree with the real Android view tree on the **UI thread**.

---

### Old Architecture vs New Architecture

This project uses `newArchEnabled=true`, which matters:

| | Old Architecture (Bridge) | New Architecture (JSI + Fabric) |
|---|---|---|
| Transport | JSON serialized over an async message queue | Direct C++ function calls via JSI |
| Modules | Lazy-loaded, reflected via Java annotations | C++ host objects, registered in `libappmodules.so` |
| Views | `UIManager` dispatches commands via the bridge | Fabric C++ renderer, synchronous layout |
| Threading | Always async, always a round-trip | Can be synchronous, no serialization overhead |
| Our modules | Would use `NativeModules` global directly | Go through TurboModule interop layer |

Because we used `SimpleViewManager` and `ReactContextBaseJavaModule` (legacy APIs), React Native wraps them in an **interop adapter** automatically. From JS's perspective the call looks identical — but under the hood, instead of going through the old JSON queue, it goes through the TurboModule/Fabric C++ layer.

---

### The `libappmodules.so` Role at Startup

This is the piece that ties it all together:

```
App launch
    │
    ├── loadReactNative(this)         ← loads libreactnative.so, libhermes.so
    │
    ├── SoLoader loads libappmodules.so
    │       │
    │       └── OnLoad.cpp (default-app-setup)
    │               registers TurboModule providers
    │               registers Fabric component descriptors
    │               wires up autolinking (react-native-screens, safe-area-context, etc.)
    │
    └── JS bundle starts executing
            TurboModuleRegistry.getEnforcing('PlatformConstants') ✓
            TurboModuleRegistry.getEnforcing('JniBridge')         ✓  ← our module
            requireNativeComponent('VideoPlayer')                 ✓  ← our view
```

`libappmodules.so` is what the JS runtime queries when it needs any native module or native component. If it is missing (which happened when we had the wrong CMake setup), every native lookup fails — including `PlatformConstants`, which React Native needs before it can render a single component.

---

## New Architecture Interop

This app runs in **New Architecture mode** (`newArchEnabled=true` in `gradle.properties`), which uses the Fabric renderer and TurboModule system. The custom native code here uses the **legacy APIs** (`SimpleViewManager`, `ReactContextBaseJavaModule`, `ReactPackage`) — this works because React Native 0.86 ships an interop layer that wraps legacy modules automatically. No codegen spec files are required.

---

## Running

```bash
npm run android
```

**Requirements:**
- Node ≥ 22
- Java 17 — `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`
- Android SDK — `export ANDROID_HOME=$HOME/Library/Android/sdk`
- A running emulator or connected device
