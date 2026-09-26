# Anti-Linkage App (防关联指纹隔离多开应用)

基于 **用户态虚拟化沙箱 (User-space Virtualization Sandbox)** 与 **现代 LibXposed API (`io.github.libxposed`)** 架构构建的高性能 Android 防关联多开系统。

---

## 架构概览

本项目采用解耦的三层模块化设计：

```
Anti-linkage app/
├── app/                      # 宿主应用层 (UI交互、多开实例管理、指纹可视化配置)
├── sandbox-core/             # 用户态虚拟化沙箱引擎 (VCore、VFS 重定向、Binder 动态代理、Activity 占坑还原)
└── fingerprint-isolation/    # 现代 LibXposed 规范指纹隔离模块 (Android ID、IMEI、MAC、Build、GPS 拦截)
```

### 1. `:fingerprint-isolation` 模块
* **标准规范**：对接 Maven 仓库 `io.github.libxposed:api:100.0.0` 现代标准。
* **拦截模型**：采用新版责任链拦截器模型 (`Hooker<T>` + `Chain<T>`)。
* **隔离维度**：
  * `Settings.Secure.ANDROID_ID`：拦截 `Settings.Secure.getString()`
  * 硬件属性：覆盖 `Build` 静态字段，拦截 `SystemProperties.get()`
  * 基带通信：拦截 `TelephonyManager` 的 `getImei()`、`getDeviceId()`、`getMeid()`
  * 物理网卡：拦截 `WifiInfo.getMacAddress()` 与 `NetworkInterface.getHardwareAddress()`
  * 地理位置：拦截 `LocationManager.getLastKnownLocation()` 虚拟 GPS 经纬度

### 2. `:sandbox-core` 模块
* **Native VFS 重定向 (C++ / JNI)**：
  * 拦截 libc 层文件操作，将目标应用的 `/data/data/<pkg>/` 重定向至各分身隔离路径 `/data/data/<host>/virtual/users/<userId>/<pkg>/`。
* **Binder 代理服务**：
  * `ServiceManagerHook`：拦截系统 `sCache`，接管 AMS、ATMS 与 PMS。
  * `IPackageManagerHook`：为未在系统注册的目标 APK 伪造返回真实的 `PackageInfo`。
  * `IActivityTaskManagerHook`：在启动组件时将目标 `TargetActivity` 包装为预埋的 `StubActivity` 规避 AMS 强校验。
* **生命周期入栈还原**：
  * `ActivityThreadHook`：挂钩 `ActivityThread.mH`，在组件即将实例化时解包并还原 `TargetActivity`。
* **Hidden API 豁免**：
  * 启动前通过 `VMRuntime.setHiddenApiExemptions` 彻底豁免 Android 9.0+ 反射限制。

### 3. `:app` 模块
* **分身实例管理**：支持创建无限数量的独立分身实例（分配独立 `userId`）。
* **一键随机指纹**：支持为每个分身一键生成或手动定制专属的手机品牌、型号、Android ID、IMEI、MAC 地址及虚拟经纬度。
* **沙箱拉起**：为每个实例绑定独立沙箱路径并载入指纹配置后直接运行。

---

## 编译与运行要求

* **IDE**：Android Studio Iguana (2023.2.1) 或更高版本
* **JDK**：Java 17 (Azul Zulu / OpenJDK 17)
* **Android SDK**：Compile SDK 34 (Android 14)，Min SDK 26 (Android 8.0)
* **NDK & CMake**：CMake 3.22.1+，NDK 25+
