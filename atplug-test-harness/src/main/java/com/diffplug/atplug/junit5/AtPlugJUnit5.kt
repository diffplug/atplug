package com.diffplug.atplug.junit5

import com.diffplug.atplug.PlugHarness
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class AtPlugJUnit5(private val harness: PlugHarness) : BeforeEachCallback, AfterEachCallback {
	private var openHarness: AutoCloseable? = null

	override fun beforeEach(context: ExtensionContext) {
		assert(openHarness == null)
		openHarness = harness.start()
	}

	override fun afterEach(context: ExtensionContext) {
		openHarness!!.close()
		openHarness = null
	}
}
