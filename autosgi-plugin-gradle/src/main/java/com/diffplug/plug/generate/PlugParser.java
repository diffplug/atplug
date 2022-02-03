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
package com.diffplug.plug.generate;


import com.diffplug.common.base.Preconditions;
import com.diffplug.common.primitives.Ints;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

public class PlugParser {
	private byte[] buffer = new byte[64 * 1024]; // CTabFolder.class

	public void parse(File file) throws IOException {
		int filelen = Ints.saturatedCast(file.length() + 1); // +1 prevents infinite loop
		if (buffer.length < filelen) {
			buffer = new byte[filelen];
		}
		int pos = 0;
		try (FileInputStream in = new FileInputStream(file)) {
			while (true) {
				int numRead = in.read(buffer, pos, filelen - pos);
				if (numRead == -1) {
					break;
				}
				pos += numRead;
			}
		}
		ClassReader reader = new ClassReader(buffer, 0, pos);
		plugClassName = asmToJava(reader.getClassName());
		socketClassName = null;
		reader.accept(classVisitor, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG | ClassReader.SKIP_CODE);
	}

	public boolean hasPlug() {
		return socketClassName != null;
	}

	public String getPlugClassName() {
		return plugClassName;
	}

	public String getSocketClassName() {
		return socketClassName;
	}

	static String asmToJava(String className) {
		return className.replace("/", ".");
	}

	private static final String PLUG = "Lcom/diffplug/autosgi/Plug;";

	private String plugClassName;
	private String socketClassName;

	private static final int API = Opcodes.ASM9;
	private final ClassVisitor classVisitor = new ClassVisitor(API) {
		@Override
		public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
			return PLUG.equals(desc) ? annotationVisitor : null;
		}
	};

	private final AnnotationVisitor annotationVisitor = new AnnotationVisitor(API) {
		@Override
		public void visit(String name, Object value) {
			Preconditions.checkArgument(name.equals("value"), "For @Plug %s, expected 'value' but was '%s'", plugClassName, name);
			Preconditions.checkArgument(socketClassName == null, "For @Plug %s, multiple annotations: '%s' and '%s'", plugClassName, socketClassName, value);
			socketClassName = ((org.objectweb.asm.Type) value).getClassName();
		}
	};
}