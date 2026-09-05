#include <jni.h>
#include <android/log.h>
#include <string.h>
#include <unistd.h>
#include <sys/ptrace.h>
#include <sys/wait.h>

#define LOG_TAG "XRC_Native"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_antiDebug(JNIEnv *env, jclass clazz) {
    LOGD("Anti-debug native check");
    // PTRACE_TRACEME - prevent debugger attachment
    if (ptrace(PTRACE_TRACEME, 0, NULL, NULL) == -1) {
        LOGE("Debugger detected via ptrace");
        // Exit silently
        _exit(1);
    }
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_hideProcess(JNIEnv *env, jclass clazz) {
    LOGD("Hide process native");
    // Attempt to hide from /proc
    // This is a stub - actual implementation would require root
}

JNIEXPORT void JNICALL
Java_com_xrc_system_native_NativeGuard_protectMemory(JNIEnv *env, jclass clazz) {
    LOGD("Protect memory native");
    // Memory protection stub
}
