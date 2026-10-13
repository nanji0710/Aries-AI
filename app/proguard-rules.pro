# ─────────────────────────────────────────────────────────────────────────────
# Aries AI — R8 规则（release 已开启 minify + shrinkResources）
#
# 原则：体积收益来自「未被使用的代码/资源被移除」，而不是把反射目标改名。
# 下面每条 keep 都是为了保住运行期按名字查找的入口；缺失只会表现为运行时崩溃，
# 因此 release 包发布前必须做一次真机冒烟：会话读写、第三方/官方 API、文档解析
# （PDF/Word/Excel）、自动化执行、语音输入。
# debug 包不参与混淆，规则对本机调试无影响。
# ─────────────────────────────────────────────────────────────────────────────

# 保留行号，便于线上堆栈定位（体积代价很小）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature

# ── Gson：会话 JSON 按字段名序列化，字段名被混淆会导致旧数据无法反序列化 ──
-keep class com.ai.phoneagent.MainActivity$UiMessage { *; }
-keep class com.ai.phoneagent.MainActivity$Conversation { *; }
-keep class com.ai.phoneagent.data.AttachmentInfo { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ── 反射入口（App 自身） ──
# core/utils/DisplayUtils.kt:111-112：Class.forName + getDeclaredField("instance") 取无障碍服务实例
-keepclassmembers class com.ai.phoneagent.PhoneAgentAccessibilityService {
    public static ** instance;
}
# ui/components/AttachmentComponents.kt:127：getDeclaredMethod("launchCamera") 调起拍照
-keepclassmembers class com.ai.phoneagent.MainActivity {
    void launchCamera();
}

# ── Apache POI / XMLBeans：schema 类型与解析器按名字加载，混淆或裁剪即运行期崩溃 ──
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class com.microsoft.schemas.** { *; }
-keep class org.openxmlformats.** { *; }
-keep class schemaorg_apache_xmlbeans.** { *; }

# ── iText7：字体/PDF 解析存在反射与 ServiceLoader 注册 ──
-keep class com.itextpdf.** { *; }

# ── 其它反射/序列化型依赖 ──
-dontwarn org.jsoup.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ── 缺失类告警抑制 ──
# POI / iText / XMLBeans 是桌面优先的库，引用了大量 Android 上不存在的可选依赖
# （java.awt、javax.imageio、org.apache.log4j、bouncycastle 等）。
# 这些引用只出现在未走到的分支里，R8 在 AGP 8+ 默认把「缺失类」当错误，
# 因此这里统一抑制告警；真正被用到的类仍由上面的 keep 保住。
-dontwarn **
