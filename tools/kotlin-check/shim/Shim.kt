package lokma.shim

/**
 * Stand-in for the few JUnit 4 calls the pure unit tests use, so they can run with a bare Kotlin compiler (no Gradle, no Android SDK,
 * no Maven access). run.sh rewrites `org.junit.*` imports to this package in a TEMP COPY; the repo's tests are not touched.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION)
annotation class Test

private fun fail(message: String?, detail: String): Nothing = throw AssertionError(if (message.isNullOrEmpty()) detail else "$message — $detail")

fun assertEquals(expected: Any?, actual: Any?) = assertEquals(null, expected, actual)
fun assertEquals(message: String?, expected: Any?, actual: Any?) { if (expected != actual) fail(message, "expected:<$expected> but was:<$actual>") }
fun assertEquals(expected: Long, actual: Long) = assertEquals(null, expected as Any?, actual as Any?)
fun assertEquals(message: String?, expected: Long, actual: Long) = assertEquals(message, expected as Any?, actual as Any?)
fun assertTrue(condition: Boolean) = assertTrue(null, condition)
fun assertTrue(message: String?, condition: Boolean) { if (!condition) fail(message, "expected true") }
fun assertFalse(condition: Boolean) = assertFalse(null, condition)
fun assertFalse(message: String?, condition: Boolean) { if (condition) fail(message, "expected false") }
fun assertNull(actual: Any?) { if (actual != null) fail(null, "expected null but was:<$actual>") }
fun assertNotNull(actual: Any?) { if (actual == null) fail(null, "expected not null") }
