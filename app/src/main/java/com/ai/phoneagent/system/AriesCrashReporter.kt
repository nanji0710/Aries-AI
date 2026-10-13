/*
 * Aries AI - Android UI Automation Framework
 * Copyright (C) 2025-2026 ZG0704666
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.ai.phoneagent.system

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃落盘器（**仅 debug 构建启用**）。
 *
 * **为什么需要**
 * - 本机测试机（Honor BKQ-AN10 / Android 17）的 ROM 屏蔽了 logcat：`adb logcat -g` 各 buffer 读出来都是
 *   0 B，`adb logcat -b crash` 拿不到任何 Java 崩溃栈，`/data/anr` 也非 root 不可读。
 * - 结果是「点某个入口就闪退」这类问题无法取证，只能靠读代码猜。
 *
 * **做什么**
 * - 进程启动时挂钩 `Thread.setDefaultUncaughtExceptionHandler`，把未捕获异常（含 cause 链）写到
 *   `filesDir/crash/last_crash.txt`，然后**继续交给系统原处理器**，不改变崩溃行为（系统仍会杀进程、
 *   仍会弹「应用已停止运行」）。
 * - 下次启动时由 [com.ai.phoneagent.MainActivity] 读取并回显成对话框，用户可直接复制堆栈。
 *
 * **注意**
 * - 写入必须"尽力而为"：自身失败绝不能掩盖原始崩溃，因此所有 IO 都包在 `runCatching` 里。
 * - release 构建不安装（避免把堆栈留在用户设备上）。
 */
object AriesCrashReporter {

    private const val DIR_NAME = "crash"
    private const val FILE_NAME = "last_crash.txt"

    /** 单份堆栈上限，避免异常风暴把文件写到几十 MB。 */
    private const val MAX_CHARS = 24_000

    @Volatile
    private var installed = false

    /**
     * 安装崩溃处理器（幂等）。必须在 `Application.onCreate` 的最早期调用，
     * 否则更早的崩溃（如 Koin 启动失败）抓不到。
     */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(appContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 上次崩溃日志文件（可能不存在）。 */
    fun crashFile(context: Context): File = File(File(context.filesDir, DIR_NAME), FILE_NAME)

    /** 读取上次崩溃日志；没有或读失败返回 null。 */
    fun readLastCrash(context: Context): String? =
        runCatching {
            val file = crashFile(context)
            if (!file.isFile) return@runCatching null
            file.readText().take(MAX_CHARS).ifBlank { null }
        }.getOrNull()

    /** 删除已回显的崩溃日志。 */
    fun clear(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val file = crashFile(context)
        file.parentFile?.mkdirs()
        val buffer = StringWriter()
        PrintWriter(buffer).use { writer ->
            writer.println("time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            writer.println("thread: ${thread.name}")
            writer.println(
                "device: ${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE} " +
                    "(SDK ${Build.VERSION.SDK_INT})",
            )
            writer.println("package: ${context.packageName}")
            writer.println("---")
            throwable.printStackTrace(writer)
            var cause = throwable.cause
            var depth = 0
            while (cause != null && depth < 5) {
                writer.println("--- cause ${depth + 1} ---")
                cause.printStackTrace(writer)
                cause = cause.cause
                depth++
            }
        }
        file.writeText(buffer.toString().take(MAX_CHARS))
    }
}
