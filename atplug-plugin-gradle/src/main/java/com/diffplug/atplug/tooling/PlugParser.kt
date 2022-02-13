/*
 * Copyright (C) 2021-2022 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.atplug.tooling

import java.io.File
import java.io.FileInputStream
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type

class PlugParser {
	private var buffer = ByteArray(64 * 1024)

	fun parse(file: File) {
		val filelen = (file.length() + 1).toInt() // +1 prevents infinite loop
		if (buffer.size < filelen) {
			buffer = ByteArray(filelen)
		}
		var pos = 0
		FileInputStream(file).use { `in` ->
			while (true) {
				val numRead = `in`.read(buffer, pos, filelen - pos)
				if (numRead == -1) {
					break
				}
				pos += numRead
			}
		}
		val reader = ClassReader(buffer, 0, pos)
		plugClassName = asmToJava(reader.className)
		socketClassName = null
		reader.accept(
				classVisitor, ClassReader.SKIP_FRAMES or ClassReader.SKIP_DEBUG or ClassReader.SKIP_CODE)
	}

	fun hasPlug(): Boolean {
		return socketClassName != null
	}

	var plugClassName: String? = null
	var socketClassName: String? = null

	private val classVisitor: ClassVisitor =
			object : ClassVisitor(API) {
				override fun visitAnnotation(desc: String, visible: Boolean): AnnotationVisitor? {
					return if (PLUG == desc) annotationVisitor else null
				}
			}
	private val annotationVisitor: AnnotationVisitor =
			object : AnnotationVisitor(API) {
				override fun visit(name: String, value: Any) {
					require(name == "value") { "For @Plug $plugClassName, expected 'value' but was '$name'" }
					require(socketClassName == null) {
						"For @Plug $plugClassName, multiple annotations: '$socketClassName' and '$value'"
					}
					socketClassName = (value as Type).className
				}
			}

	companion object {
		fun asmToJava(className: String): String {
			return className.replace("/", ".")
		}

		private const val PLUG = "Lcom/diffplug/atplug/Plug;"
		private const val API = Opcodes.ASM9
	}
}
