package app.ownyourtv

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** An ADB shell on this TV, opened over its own network debugging port. */
class Tv private constructor(private val adb: Dadb) : AutoCloseable {

    data class Result(val code: Int, val out: String) {
        val ok get() = code == 0
    }

    fun sh(cmd: String): Result {
        val r = adb.shell(cmd)
        return Result(r.exitCode, (r.output + r.errorOutput).trim())
    }

    fun install(apk: File): Boolean = runCatching { adb.install(apk); true }.getOrDefault(false)

    override fun close() = runCatching { adb.close() }.let { }

    companion object {
        const val PORT = 5555

        /** True when something listens on the debugging port, so a connection can be asked for. */
        fun portOpen(): Boolean = runCatching {
            Socket().use { it.connect(InetSocketAddress("127.0.0.1", PORT), 1500) }
            true
        }.getOrDefault(false)

        /** Blocks until the owner answers the TV's "Allow debugging?" window. */
        fun connect(ctx: Context): Tv {
            val priv = File(ctx.filesDir, "adbkey")
            val pub = File(ctx.filesDir, "adbkey.pub")
            if (!priv.exists() || !pub.exists()) AdbKeyPair.generate(priv, pub)
            val adb = Dadb.create("127.0.0.1", PORT, AdbKeyPair.read(priv, pub))
            val tv = Tv(adb)
            tv.sh("echo ok")
            return tv
        }
    }
}
