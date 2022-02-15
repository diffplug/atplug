/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.lang.Exception
import java.lang.IllegalArgumentException
import java.lang.RuntimeException
import java.lang.reflect.Modifier
import java.util.*
import java.util.function.Function
import java.util.function.Predicate

abstract class SocketOwner<T>(val socketClass: Class<T>) {
	abstract fun metadata(plug: T): Map<String, String>

	fun asDescriptor(plug: T) =
			PlugDescriptor(plug!!::class.java.name, socketClass.name, metadata(plug)).toJson()

	/**
	 * Instantiates the given plug. Already implemented by the default implementations [Id] and
	 * [Complex]. If you implement this yourself, make sure that you call [PlugRegistry.register] in
	 * your constructor.
	 */
	protected abstract fun instantiatePlug(plugDescriptor: PlugDescriptor): T

	internal fun doRegister(plugDescriptor: PlugDescriptor) {
		register(plugDescriptor)
	}

	protected abstract fun register(plugDescriptor: PlugDescriptor)

	internal fun doRemove(plugDescriptor: PlugDescriptor) {
		remove(plugDescriptor)
	}

	protected abstract fun remove(plugDescriptor: PlugDescriptor)

	abstract class Complex<T, ParsedDescriptor>(socketClass: Class<T>) : SocketOwner<T>(socketClass) {
		private val descriptors = mutableMapOf<ParsedDescriptor, PlugDescriptor>()
		init {
			PlugRegistry.register(socketClass, this)
		}

		override fun instantiatePlug(plugDescriptor: PlugDescriptor): T =
				PlugRegistry.instantiatePlug(socketClass, plugDescriptor)

		abstract protected fun parse(plugDescriptor: PlugDescriptor): ParsedDescriptor

		protected fun all() = descriptors.keys

		protected fun descriptorFiltered(
				predicate: Predicate<ParsedDescriptor>
		): List<ParsedDescriptor> {
			synchronized(this) {
				return descriptors.keys.filter { predicate.test(it) }
			}
		}

		protected fun instantiateFiltered(predicate: Predicate<ParsedDescriptor>): List<T> {
			val result = mutableListOf<T>()
			synchronized(this) {
				descriptors.forEach { (parsed, descriptor) ->
					if (predicate.test(parsed)) {
						result.add(instantiatePlug(descriptor))
					}
				}
			}
			return result
		}

		override fun register(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val parsed = parse(plugDescriptor)
				descriptors[parsed] = plugDescriptor
				registerHook(plugDescriptor)
			}
		}

		override fun remove(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val iterator = descriptors.iterator()
				while (iterator.hasNext()) {
					if (iterator.next().value == plugDescriptor) {
						iterator.remove()
						removeHook(plugDescriptor)
					}
				}
			}
		}

		open fun registerHook(plugDescriptor: PlugDescriptor) {}

		open fun removeHook(plugDescriptor: PlugDescriptor) {}
	}

	abstract open class Id<T>(socketClass: Class<T>) : SocketOwner<T>(socketClass) {
		private val descriptorById = mutableMapOf<String, PlugDescriptor>()
		private val instanceById = mutableMapOf<String, T>()
		init {
			PlugRegistry.register(socketClass, this)
		}

		override fun instantiatePlug(plugDescriptor: PlugDescriptor): T =
				PlugRegistry.instantiatePlug(socketClass, plugDescriptor)

		override final fun register(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val id = plugDescriptor.properties[KEY_ID]!!
				descriptorById[id] = plugDescriptor
				registerHook(plugDescriptor)
			}
		}

		override final fun remove(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val id = plugDescriptor.properties[KEY_ID]!!
				val removed = descriptorById.remove(id)
				assert(removed != null)
				instanceById.remove(id)
				removeHook(plugDescriptor)
			}
		}

		open fun registerHook(plugDescriptor: PlugDescriptor) {}

		open fun removeHook(plugDescriptor: PlugDescriptor) {}

		fun allIds() = Collections.unmodifiableSet(descriptorById.keys)

		fun forId(id: String): T? {
			synchronized(this) {
				try {
					return instanceById.computeIfAbsent(id) { instantiatePlug(descriptorById[it]!!) }
				} catch (e: NullPointerException) {
					return null
				}
			}
		}

		fun descriptorForId(id: String): PlugDescriptor? {
			synchronized(this) {
				return descriptorById[id]
			}
		}
	}

	companion object {
		const val KEY_ID = "id"

		fun <T> metadataGeneratorFor(socketClass: Class<T>): Function<T, String> {
			var firstAttempt: Throwable? = null
			try {
				val socketField = socketClass.getDeclaredField("socket")!!
				if (Modifier.isStatic(socketField.modifiers) && Modifier.isFinal(socketField.modifiers)) {
					val socket = socketField[null] as SocketOwner<T>
					return generatorForSocket(socket)
				}
			} catch (e: Throwable) {
				firstAttempt = e
			}
			try {
				val socketOwnerClass = Class.forName(socketClass.name + "\$Socket").kotlin
				val socket = socketOwnerClass.objectInstance!! as SocketOwner<T>
				return generatorForSocket(socket)
			} catch (secondAttempt: Throwable) {
				val e =
						IllegalArgumentException(
								"To create metadata for `$socketClass` we need either a field `static final SocketOwner socket` or a kotlin `object Socket`.",
								secondAttempt)
				firstAttempt?.let(e::addSuppressed)
				throw e
			}
		}

		private fun <T> generatorForSocket(socket: SocketOwner<T>): Function<T, String> {
			return Function { plug ->
				try {
					socket.asDescriptor(plug)
				} catch (e: Exception) {
					if (rootCause(e) is ClassNotFoundException) {
						throw RuntimeException(
								"Unable to generate metadata for " +
										plug!!::class.java +
										", missing transitive dependency " +
										rootCause(e).message,
								e)
					} else {
						throw RuntimeException(
								"Unable to generate metadata for " +
										plug!!::class.java +
										", make sure that its metadata methods return simple constants: " +
										e.message,
								e)
					}
				}
			}
		}

		private fun rootCause(e: Throwable): Throwable = e.cause?.let { rootCause(it) } ?: e
	}
}
