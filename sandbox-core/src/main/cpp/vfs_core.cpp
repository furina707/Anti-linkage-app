#include "vfs_core.h"
#include <android/log.h>
#include <string>
#include <mutex>
#include <cstring>
#include <unistd.h>
#include <sys/stat.h>

#define TAG "VFS_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::string g_host_pkg;
static std::string g_target_pkg;
static int g_user_id = 0;
static std::string g_target_prefix1;
static std::string g_target_prefix2;
static std::string g_isolated_base_path;
static std::mutex g_vfs_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_antilinkage_sandbox_vfs_VFileSystem_nativeInitVFS(
        JNIEnv* env,
        jclass clazz,
        jstring hostPkg,
        jstring targetPkg,
        jint userId) {
    std::lock_guard<std::mutex> lock(g_vfs_mutex);

    const char* c_host = env->GetStringUTFChars(hostPkg, nullptr);
    const char* c_target = env->GetStringUTFChars(targetPkg, nullptr);

    g_host_pkg = c_host ? c_host : "";
    g_target_pkg = c_target ? c_target : "";
    g_user_id = userId;

    env->ReleaseStringUTFChars(hostPkg, c_host);
    env->ReleaseStringUTFChars(targetPkg, c_target);

    // 标准应用数据路径前缀
    g_target_prefix1 = "/data/data/" + g_target_pkg;
    g_target_prefix2 = "/data/user/0/" + g_target_pkg;

    // 宿主私有隔离目录: /data/data/<host>/virtual/users/<userId>/<targetPkg>
    g_isolated_base_path = "/data/data/" + g_host_pkg + "/virtual/users/" +
                           std::to_string(g_user_id) + "/" + g_target_pkg;

    LOGI("Native VFS Initialized. Redirecting [%s] -> [%s]",
         g_target_prefix1.c_str(), g_isolated_base_path.c_str());

    return JNI_TRUE;
}

std::string redirect_c_path(const std::string& path) {
    if (g_target_pkg.empty() || g_isolated_base_path.empty()) {
        return path;
    }

    if (path.rfind(g_target_prefix1, 0) == 0) {
        return g_isolated_base_path + path.substr(g_target_prefix1.length());
    }
    if (path.rfind(g_target_prefix2, 0) == 0) {
        return g_isolated_base_path + path.substr(g_target_prefix2.length());
    }

    return path;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_antilinkage_sandbox_vfs_VFileSystem_nativeRedirectPath(
        JNIEnv* env,
        jclass clazz,
        jstring rawPath) {
    if (!rawPath) return nullptr;

    const char* c_raw = env->GetStringUTFChars(rawPath, nullptr);
    std::string path_str = c_raw ? c_raw : "";
    env->ReleaseStringUTFChars(rawPath, c_raw);

    std::string redirected;
    {
        std::lock_guard<std::mutex> lock(g_vfs_mutex);
        redirected = redirect_c_path(path_str);
    }

    return env->NewStringUTF(redirected.c_str());
}
