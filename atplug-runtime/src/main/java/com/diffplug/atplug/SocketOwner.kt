/*
 * Copyright (C) 2022 DiffPlug, LLC - All Rights Reserved
 * Unauthorized copying of this file via any medium is strictly prohibited.
 * Proprietary and confidential.
 * Please send any inquiries to Ned Twigg <ned.twigg@diffplug.com>
 */
package com.diffplug.atplug

import java.util.*
import java.util.function.Predicate

abstract class SocketOwner<T>() {
	abstract fun metadata(plug: T): Map<String, String>

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
		register(plugDescriptor)
	}

	protected abstract fun remove(plugDescriptor: PlugDescriptor)

	abstract class Complex<T, ParsedDescriptor>(private val socketClass: Class<T>) :
			SocketOwner<T>() {
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

	abstract open class Id<T>(private val socketClass: Class<T>) : SocketOwner<T>() {
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

		companion object {
			const val KEY_ID = "id"
		}
	}
}
