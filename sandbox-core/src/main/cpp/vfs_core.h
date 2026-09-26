#ifndef ANTI_LINKAGE_VFS_CORE_H
#define ANTI_LINKAGE_VFS_CORE_H

#include <jni.h>
#include <string>

#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jboolean JNICALL
Java_com_antilinkage_sandbox_vfs_VFileSystem_nativeInitVFS(
        JNIEnv* env,
        jclass clazz,
        jstring hostPkg,
        jstring targetPkg,
        jint userId
);

JNIEXPORT jstring JNICALL
Java_com_antilinkage_sandbox_vfs_VFileSystem_nativeRedirectPath(
        JNIEnv* env,
        jclass clazz,
        jstring rawPath
);

#ifdef __cplusplus
}
#endif

#endif // ANTI_LINKAGE_VFS_CORE_H
