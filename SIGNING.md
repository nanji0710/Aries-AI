# 构建与签名说明

## 快速构建

```bash
# Debug 包（含 debuggable 标记，会触发系统「Android 应用兼容性」提示）
./gradlew assembleDebug

# Release 包（已签名、R8 压缩、无 debuggable 标记）
./gradlew assembleRelease
```

产物路径：

| 变体 | 路径 | 体积（参考） |
|------|------|--------------|
| debug | `app/build/outputs/apk/debug/app-debug.apk` | ~44 MB |
| release | `app/build/outputs/apk/release/app-release.apk` | ~18 MB |

---

## 签名配置

### 默认密钥库（公开，仅供自构建/测试）

仓库根目录的 `aries-release.jks` 是随代码公开的默认签名密钥，**任何克隆本仓库的人都能用它构建出可安装的 release 包**，无需自己生成密钥。

| 项 | 值 |
|----|-----|
| 密钥库文件 | `aries-release.jks`（PKCS12） |
| Key Alias | `aries` |
| Store Password | `Aries@2026Release` |
| Key Password | `Aries@2026Release` |
| 密钥算法 | RSA 2048 |
| 有效期 | 至 2054-02-28 |
| DN | `CN=Aries AI, OU=Mobile, O=Aries, L=Nanjing, ST=Jiangsu, C=CN` |

**证书指纹：**

```
SHA-256: 86:5A:B9:EF:40:16:2C:4E:81:F8:E3:47:E9:9E:73:86:C9:56:B5:D0:92:44:13:69:60:A3:11:3E:E8:E9:49:EA
SHA-1:   10:B6:60:1A:FE:03:7A:7B:C3:93:80:A1:AC:22:D4:97:FB:03:AF:09
MD5:     41:13:D1:89:6A:CF:D3:55:15:CB:62:53:C3:E9:C2:7F
```

> ⚠️ **这个密钥是公开的，不能用于应用商店发布。** 上架（Google Play / 国内应用市场）必须另生成私有密钥。若你只是自行构建、内部分发或测试，用这套默认密钥即可。

### 使用自己的密钥（推荐用于正式发布）

在仓库根目录创建 `local.properties`（该文件已被 `.gitignore` 屏蔽，不会提交）：

```properties
aries.release.storeFile=/absolute/path/to/your-release.jks
aries.release.storePassword=你的口令
aries.release.keyAlias=你的alias
aries.release.keyPassword=你的口令
```

`app/build.gradle.kts` 的 `signingConfigs.release` 会读取这四个属性；未配置时回退到上面的默认密钥库。

生成新密钥库：

```bash
keytool -genkeypair -v \
  -keystore your-release.jks \
  -alias youralias \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass '<口令>' -keypass '<口令>' \
  -dname "CN=Your Name, OU=Mobile, O=Your Org, L=City, ST=Province, C=CN"
```

### 校验签名

```bash
"$ANDROID_HOME/build-tools/<version>/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

---

## 16 KB 页对齐

Android 15（API 35）起，Google 要求 64 位 native 库按 **16 KB 页大小**对齐（`p_align = 0x4000`）。未对齐的库会在设备上触发系统「Android 应用兼容性」对话框，并在日志中列出违规的 `.so`。

本项目的对齐现状：

| 库 | 来源 | 对齐 |
|----|------|------|
| `libMNN.so`、`libmnnllmapp.so` | `app/src/main/jniLibs/` 预编译 | ✅ `0x4000` |
| `libc++_shared.so` | `app/src/main/jniLibs/` 预编译 | ✅ `0x4000`（已用 `tools/align_elf_16k.py` 重对齐） |
| `libquickjs.so` | 依赖 AAR | ✅ `0x4000` |
| `libdatastore_shared_counter.so` | 依赖 AAR | ✅ `0x4000` |
| `libandroidx.graphics.path.so` | 依赖 AAR | ✅ `0x4000` |
| `libsherpa-ncnn-jni.so` | CMake 构建 | ✅ `0x4000` |

CMake 侧的保障（`app/src/main/cpp/CMakeLists.txt` 与 `app/build.gradle.kts`）：

```cmake
add_link_options("-Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
```

```kotlin
externalNativeBuild {
    cmake {
        arguments += listOf("-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
    }
}
```

**注意：** 上述 CMake 参数只对由 CMake 构建的库生效，**管不到 `jniLibs/` 下手工检入的预编译 `.so`**。若日后替换或新增 `jniLibs/` 中的库，必须用下面的脚本重新校验/对齐。

### 校验与修复工具

检查某个 `.so` 的 LOAD 段对齐（`Align` 列应为 `0x4000` 或 `0x10000`）：

```bash
"$ANDROID_HOME/ndk/<version>/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf" -lW your.so
```

若某库仍是 `0x1000`，用仓库自带脚本重对齐（重建程序头，保持 SONAME / 动态符号 / bss 不变）：

```bash
python tools/align_elf_16k.py path/to/libfoo.so path/to/libfoo.so.fixed
```

校验全部打包进 APK 的库：

```bash
# 解压后逐个检查
unzip -o app/build/outputs/apk/release/app-release.apk 'lib/*/*.so' -d /tmp/socheck
for f in /tmp/socheck/lib/*/*.so; do echo "== $f"; llvm-readelf -lW "$f" | grep LOAD; done
```
