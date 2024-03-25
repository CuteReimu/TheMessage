package com.fengsheng.handler

import org.apache.commons.text.CaseUtils
import java.io.File

class Main {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // 读取本文件夹下的所有文件
            val files = File("src/main/kotlin/handler").listFiles()
            for (file in files!!) {
                if ("_" in file.name && file.extension.lowercase() == "kt") {
                    val clsName = file.name.dropLast(3)
                    val clsName2 = CaseUtils.toCamelCase(clsName, true, '_')
                    // 读取这个文件
                    val lines = file.readLines()
                    var ok = false
                    val newLines = lines.map {
                        if ("class $clsName" in it) {
                            ok = true
                            it.replaceFirst("class $clsName", "class $clsName2")
                        } else {
                            it
                        }
                    }
                    if (ok) {
                        file.writeText(newLines.joinToString("\n"))
                        file.renameTo(File("src/main/kotlin/handler/$clsName2.kt"))
                    }
                }
            }
        }
    }
}
