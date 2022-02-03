/*
 * Copyright (C) 2016-2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.plug.generate.gradle;


import com.diffplug.common.base.Errors;
import com.diffplug.common.base.Throwing;
import com.diffplug.gradle.FileMisc;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;

public interface JavaExecable extends Serializable, Throwing.Runnable {
	interface PlugParameters extends WorkParameters {
		Property<JavaExecable> getInput();

		RegularFileProperty getOutputFile();
	}

	abstract class PlugAction implements WorkAction<PlugParameters> {
		@Override
		public void execute() {
			JavaExecable gen = getParameters().getInput().get();
			try {
				gen.run();
				write(getParameters().getOutputFile().get().getAsFile(), gen);
			} catch (Throwable e) {
				throw Errors.asRuntime(e);
			}
		}
	}

	static <T extends JavaExecable> T exec(WorkQueue queue, T input) throws Throwable {
		File tempFile = File.createTempFile("JavaExecQueue", ".temp");
		queue.submit(PlugAction.class, action -> {
			action.getInput().set(input);
			action.getOutputFile().set(tempFile);
		});
		queue.await();

		T result = read(tempFile);
		FileMisc.forceDelete(tempFile);
		return result;
	}

	static void main(String[] args) throws IOException {
		File file = new File(args[0]);
		try {
			// read the target object from the file
			JavaExecable javaExecOutside = read(file);
			// run the object's run method
			javaExecOutside.run();
			// save the object back to file
			write(file, javaExecOutside);
		} catch (Throwable t) {
			// if it's an exception, write it out to file
			writeThrowable(file, t);
		}
	}

	/** Writes the given object to the given file. */
	public static <T extends Serializable> void write(File file, T object) throws IOException {
		try (ObjectOutputStream output = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file)))) {
			output.writeObject(object);
		}
	}

	/** Reads an object from the given file. */
	@SuppressWarnings("unchecked")
	static <T extends Serializable> T read(File file) throws ClassNotFoundException, IOException {
		try (ObjectInputStream input = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file)))) {
			return (T) input.readObject();
		}
	}

	/** Writes an exception to file, even if that exception isn't serializable. */
	static void writeThrowable(File file, Throwable object) throws IOException {
		try {
			// write the exception as-is
			write(file, object);
		} catch (NotSerializableException e) {
			// if the exception is not serializable, then we'll
			// copy it in a way that is guaranteed to be serializable
			write(file, new ThrowableCopy(object));
		}
	}

	/** Copies an exception hierarchy (class, message, and stacktrace). */
	class ThrowableCopy extends Throwable {
		private static final long serialVersionUID = -4674520369975786435L;

		ThrowableCopy(Throwable source) {
			super(source.getClass().getName() + ": " + source.getMessage());
			setStackTrace(source.getStackTrace());
			if (source.getCause() != null) {
				initCause(new ThrowableCopy(source.getCause()));
			}
		}
	}
}
