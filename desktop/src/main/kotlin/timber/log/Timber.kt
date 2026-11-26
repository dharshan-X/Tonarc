package timber.log

/**
 * Lightweight JVM logging shim compatible with shared pure-Kotlin modules that call Timber.
 */
object Timber {
    fun d(message: String, vararg args: Any?) {
        println("[D] ${format(message, *args)}")
    }

    fun d(t: Throwable?, message: String? = null, vararg args: Any?) {
        if (message != null) println("[D] ${format(message, *args)}")
        t?.printStackTrace()
    }

    fun w(message: String, vararg args: Any?) {
        System.err.println("[W] ${format(message, *args)}")
    }

    fun w(t: Throwable?, message: String? = null, vararg args: Any?) {
        if (message != null) System.err.println("[W] ${format(message, *args)}")
        t?.printStackTrace()
    }

    fun e(message: String, vararg args: Any?) {
        System.err.println("[E] ${format(message, *args)}")
    }

    fun e(t: Throwable?, message: String? = null, vararg args: Any?) {
        if (message != null) System.err.println("[E] ${format(message, *args)}")
        t?.printStackTrace()
    }

    fun e(t: Throwable) {
        t.printStackTrace()
    }

    fun tag(tag: String): Tree = Tree(tag)

    class Tree(private val tag: String) {
        fun d(message: String, vararg args: Any?) = println("[$tag][D] ${format(message, *args)}")
        fun w(message: String, vararg args: Any?) = System.err.println("[$tag][W] ${format(message, *args)}")
        fun w(t: Throwable?, message: String? = null, vararg args: Any?) {
            if (message != null) System.err.println("[$tag][W] ${format(message, *args)}")
            t?.printStackTrace()
        }
        fun e(message: String, vararg args: Any?) = System.err.println("[$tag][E] ${format(message, *args)}")
        fun e(t: Throwable?, message: String? = null, vararg args: Any?) {
            if (message != null) System.err.println("[$tag][E] ${format(message, *args)}")
            t?.printStackTrace()
        }
    }

    private fun format(message: String, vararg args: Any?): String {
        return if (args.isEmpty()) message else runCatching { message.format(*args) }.getOrDefault(message)
    }
}
