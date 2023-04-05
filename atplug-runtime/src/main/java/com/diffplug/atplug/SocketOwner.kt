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
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate

abstract class SocketOwner<T : Any>(val socketClass: Class<T>) {
	abstract fun metadata(plug: T): Map<String, String>

	fun asDescriptor(plug: T) =
			PlugDescriptor(plug::class.java.name, socketClass.name, metadata(plug)).toJson()

	/**
	 * Instantiates the given plug. Already implemented by the default implementations [SingletonById]
	 * and [EphemeralByDescriptor]. If you implement this yourself, make sure that you call
	 * [PlugRegistry.registerSocket] in your constructor.
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

	abstract class EphemeralByDescriptor<T : Any, ParsedDescriptor>(socketClass: Class<T>) :
			SocketOwner<T>(socketClass) {
		private val descriptors = mutableMapOf<ParsedDescriptor, PlugDescriptor>()
		init {
			PlugRegistry.registerSocket(socketClass, this)
		}

		override fun instantiatePlug(plugDescriptor: PlugDescriptor): T =
				PlugRegistry.instantiatePlug(socketClass, plugDescriptor)

		protected abstract fun parse(plugDescriptor: PlugDescriptor): ParsedDescriptor

		protected fun <R : Any> computeAgainstDescriptors(
				compute: Function<Set<ParsedDescriptor>, R>
		): R {
			synchronized(this) {
				return compute.apply(descriptors.keys)
			}
		}

		protected fun forEachDescriptor(forEach: Consumer<ParsedDescriptor>) {
			synchronized(this) { descriptors.keys.forEach(forEach) }
		}

		protected fun descriptorsFor(predicate: Predicate<ParsedDescriptor>): List<ParsedDescriptor> {
			synchronized(this) {
				return descriptors.keys.filter { predicate.test(it) }
			}
		}

		protected fun instantiateFor(predicate: Predicate<ParsedDescriptor>): List<T> {
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

		protected fun instantiateFirst(
				predicateDescriptor: Predicate<ParsedDescriptor>,
				order: Comparator<ParsedDescriptor>,
				predicateInstance: Predicate<T>
		): T? {
			synchronized(this) {
				return descriptors.keys
						.filter { predicateDescriptor.test(it) }
						.sortedWith(order)
						.map { instantiatePlug(descriptors[it]!!) }
						.firstOrNull { predicateInstance.test(it) }
			}
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

		/** If you override this, make sure you also override [removeHook] */
		open fun registerHook(plugDescriptor: PlugDescriptor) {}

		open fun removeHook(plugDescriptor: PlugDescriptor) {}
	}

	abstract class SingletonById<T : Any>(socketClass: Class<T>) : SocketOwner<T>(socketClass) {
		private val descriptorById = mutableMapOf<String, PlugDescriptor>()
		private val singletonById = mutableMapOf<String, T>()
		init {
			PlugRegistry.registerSocket(socketClass, this)
		}

		override fun instantiatePlug(plugDescriptor: PlugDescriptor): T =
				PlugRegistry.instantiatePlug(socketClass, plugDescriptor)

		final override fun register(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val id = plugDescriptor.properties[KEY_ID]!!
				descriptorById[id] = plugDescriptor
				registerHook(plugDescriptor)
			}
		}

		final override fun remove(plugDescriptor: PlugDescriptor) {
			synchronized(this) {
				val id = plugDescriptor.properties[KEY_ID]!!
				val removed = descriptorById.remove(id)
				assert(removed != null)
				singletonById.remove(id)
				removeHook(plugDescriptor)
			}
		}

		/** If you override this, make sure you also override [removeHook] */
		protected open fun registerHook(plugDescriptor: PlugDescriptor) {}

		protected open fun removeHook(plugDescriptor: PlugDescriptor) {}

		fun availableIds(): List<String> {
			synchronized(this) {
				return descriptorById.keys.toList()
			}
		}

		fun descriptorForId(id: String): PlugDescriptor? {
			synchronized(this) {
				return descriptorById[id]
			}
		}

		fun singletonForId(id: String): T? {
			synchronized(this) {
				return try {
					singletonById.computeIfAbsent(id) { instantiatePlug(descriptorById[it]!!) }
				} catch (e: NullPointerException) {
					null
				}
			}
		}
	}

	companion object {
		const val KEY_ID = "id"

		fun <T : Any> metadataGeneratorFor(socketClass: Class<T>): Function<T, String> {
			var firstAttempt: Throwable? = null
			try {
				val socketField = socketClass.getDeclaredField("socket")
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

		private fun <T : Any> generatorForSocket(socket: SocketOwner<T>): Function<T, String> {
			return Function { plug ->
				try {
					socket.asDescriptor(plug)
				} catch (e: Exception) {
					if (rootCause(e) is ClassNotFoundException) {
						throw RuntimeException(
								"Unable to generate metadata for " +
										plug::class.java +
										", missing transitive dependency " +
										rootCause(e).message,
								e)
					} else {
						throw RuntimeException(
								"Unable to generate metadata for " +
										plug::class.java +
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
