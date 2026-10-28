package lokma.shim

import java.lang.reflect.InvocationTargetException
import kotlin.system.exitProcess

/** java -cp … lokma.shim.RunnerKt com.example.FooTest ... — runs every @Test method on a fresh instance (JUnit semantics). */
fun main(args: Array<String>) {
    var passed = 0; var failed = 0
    for (name in args) {
        val cls = Class.forName(name)
        val methods = cls.declaredMethods.filter { it.isAnnotationPresent(Test::class.java) }.sortedBy { it.name }
        println("\n$name  (${methods.size} tests)")
        for (m in methods) {
            try { m.invoke(cls.getDeclaredConstructor().newInstance()); passed++; println("  PASS ${m.name.replace('_', ' ')}") }
            catch (e: InvocationTargetException) { failed++; println("  FAIL ${m.name}\n      ${e.targetException}") }
        }
    }
    println("\n${if (failed == 0) "ALL PASSED" else "FAILED"} — $passed passed, $failed failed")
    exitProcess(if (failed == 0) 0 else 1)
}
