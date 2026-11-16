// ============================================================================
// integrity-checksums.gradle.kts — offline release-hash extractor & verifier
// ----------------------------------------------------------------------------
// Applied from app/build.gradle.kts. Registers two tasks:
//
//   :app:printReleaseChecksums   Reads a built, SIGNED release APK, computes
//                                 - SHA-256 of every classes*.dex
//                                 - SHA-256 of lib/<abi>/libintegrity_guard.so
//                                   with its IgBlob region zeroed
//                                 - SHA-256 of the signing certificate (from the
//                                   APK Signing Block v2/v3/v3.1)
//                                prints them, and (unless
//                                -PintegrityWriteHeader=false) rewrites
//                                app/src/main/cpp/expected_hashes.h with fresh
//                                random XOR shares.
//
//   :app:verifyReleaseChecksums  Recomputes the hashes from the (rebuilt) APK
//                                and fails the build if they don't match the
//                                "IG-EXPECT" lines in expected_hashes.h.
//
// Properties:
//   -PintegrityApk=path/to.apk          default build/outputs/apk/release/app-release.apk
//                                       (for AABs: a bundletool --mode=universal APK)
//   -PintegrityCertSha256=AB:CD:...     override the cert hash (Play App Signing!)
//   -PintegrityHeader=path              default src/main/cpp/expected_hashes.h
//   -PintegrityWriteHeader=false        print only
//
// The algorithm MUST stay byte-for-byte in sync with integrity_guard.cpp.
// ============================================================================

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.zip.ZipFile

val igMagic: ByteArray = intArrayOf(
    0x49, 0x47, 0x7F, 0x48, 0x41, 0x53, 0x48, 0x42,
    0x4C, 0x42, 0x01, 0x76, 0x31, 0xA5, 0x5A, 0xC3,
).map { it.toByte() }.toByteArray()
val igMaxDex = 6
val igAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
val igBlobSize = 32 + 64 + igMaxDex * 64 + igAbis.size * 64 // 736
val igSoName = "libintegrity_guard.so"

data class IgHashes(
    val dex: List<Pair<String, ByteArray>>,
    val so: Map<String, ByteArray>,
    val cert: ByteArray,
    val certSource: String,
)

fun igSha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
fun igHex(b: ByteArray): String = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
fun igColonHex(b: ByteArray): String = b.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
fun igParseHex(s: String): ByteArray {
    val clean = s.replace(":", "").replace(" ", "").lowercase()
    require(clean.length == 64 && clean.all { it in "0123456789abcdef" }) {
        "Expected a 32-byte SHA-256 hex string, got '$s'"
    }
    return ByteArray(32) { i -> clean.substring(2 * i, 2 * i + 2).toInt(16).toByte() }
}

fun igIndexOfAll(hay: ByteArray, needle: ByteArray): List<Int> {
    val hits = mutableListOf<Int>()
    outer@ for (i in 0..hay.size - needle.size) {
        for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
        hits += i
    }
    return hits
}

/** SHA-256 of the .so with the IgBlob region zeroed (mirrors so_hash_zeroed()). */
fun igSoHashZeroed(so: ByteArray): ByteArray {
    val hits = igIndexOfAll(so, igMagic)
    require(hits.size == 1) { "IgBlob marker found ${hits.size} times in $igSoName (expected exactly 1)" }
    val copy = so.copyOf()
    require(hits[0] + igBlobSize <= copy.size) { "IgBlob truncated" }
    java.util.Arrays.fill(copy, hits[0], hits[0] + igBlobSize, 0.toByte())
    return igSha256(copy)
}

/** classes.dex -> 1, classes2.dex -> 2 ... (mirrors dex_index()). */
fun igDexIndex(name: String): Int {
    val m = Regex("^classes([1-9][0-9]{0,2})?\\.dex$").matchEntire(name) ?: return 0
    val n = m.groupValues[1]
    if (n.isEmpty()) return 1
    return n.toInt().takeIf { it >= 2 } ?: 0
}

/** First signer certificate of every v2/v3/v3.1 block (mirrors apk_signing_certs()). */
fun igApkSigningCerts(apk: File): List<Pair<String, ByteArray>> {
    val d = apk.readBytes()
    val bb = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN)
    var eocd = -1
    val lowest = maxOf(0, d.size - 22 - 0xFFFF)
    for (i in d.size - 22 downTo lowest) {
        if (bb.getInt(i) == 0x06054b50 && i + 22 + (bb.getShort(i + 20).toInt() and 0xFFFF) == d.size) {
            eocd = i; break
        }
    }
    require(eocd >= 0) { "Not a ZIP/APK: ${apk.path}" }
    val cdOff = bb.getInt(eocd + 16).toLong() and 0xFFFFFFFFL
    require(cdOff >= 32) { "No APK Signing Block (is the APK signed with v2+?)" }
    val magic = String(d, (cdOff - 16).toInt(), 16, Charsets.US_ASCII)
    require(magic == "APK Sig Block 42") { "No APK Signing Block found. Sign with v2/v3 (minSdk 26 => AGP does this)." }
    val bsize = bb.getLong((cdOff - 24).toInt())
    val start = (cdOff - (bsize + 8)).toInt()
    require(bb.getLong(start) == bsize) { "Corrupt APK Signing Block" }
    val out = mutableListOf<Pair<String, ByteArray>>()
    var p = start + 8
    val end = (cdOff - 24).toInt()
    val schemes = mapOf(0x7109871a to "v2", 0xf05368c0.toInt() to "v3", 0x1b93ad61 to "v3.1")
    while (p < end) {
        val len = bb.getLong(p)
        require(len >= 4 && len <= end - p - 8) { "Corrupt APK Signing Block pair" }
        val id = bb.getInt(p + 8)
        schemes[id]?.let { scheme ->
            // value: lp(signers) -> lp(signer) -> lp(signed data) -> lp(digests), lp(certs) -> lp(cert)
            fun lp(off: Int): Pair<Int, Int> = off + 4 to bb.getInt(off)
            val (signersOff, signersLen) = lp(p + 12)
            val (signerOff, signerLen) = lp(signersOff)
            require(signerLen + 4 == signersLen) { "$scheme block has more than one signer — unsupported" }
            val (sdOff, _) = lp(signerOff)
            val (digOff, digLen) = lp(sdOff)
            val (certsOff, _) = lp(digOff + digLen)
            val (certOff, certLen) = lp(certsOff)
            out += scheme to d.copyOfRange(certOff, certOff + certLen)
        }
        p += 8 + len.toInt()
    }
    require(out.isNotEmpty()) { "APK Signing Block has no v2/v3 signature" }
    return out
}

fun igCompute(apk: File, certOverride: String?): IgHashes {
    require(apk.isFile) {
        "APK not found: ${apk.path}\nBuild it first: ./gradlew :app:assembleRelease (or pass -PintegrityApk=...)"
    }
    val dex = mutableListOf<Pair<String, ByteArray>>()
    val so = linkedMapOf<String, ByteArray>()
    ZipFile(apk).use { zip ->
        val entries = zip.entries().toList()
        entries.filter { igDexIndex(it.name) > 0 }
            .sortedBy { igDexIndex(it.name) }
            .forEachIndexed { i, e ->
                require(igDexIndex(e.name) == i + 1) { "Non-contiguous dex numbering at ${e.name}" }
                dex += e.name to igSha256(zip.getInputStream(e).use { it.readBytes() })
            }
        for (abi in igAbis) {
            val e = zip.getEntry("lib/$abi/$igSoName") ?: continue
            so[abi] = igSoHashZeroed(zip.getInputStream(e).use { it.readBytes() })
        }
    }
    require(dex.isNotEmpty()) { "No classes.dex in ${apk.path}" }
    require(dex.size <= igMaxDex) { "${dex.size} dex files > IG_MAX_DEX=$igMaxDex. Raise it in BOTH integrity_guard.cpp and this script." }
    require(so.isNotEmpty()) { "No lib/<abi>/$igSoName in ${apk.path}" }

    val certs = igApkSigningCerts(apk)
    val hashes = certs.map { it.first to igSha256(it.second) }
    require(hashes.map { igHex(it.second) }.distinct().size == 1) {
        "Signing blocks disagree (key rotation?): " + hashes.joinToString { "${it.first}=${igHex(it.second)}" }
    }
    val apkCert = hashes.first().second
    return if (certOverride.isNullOrBlank()) {
        IgHashes(dex, so, apkCert, "APK Signing Block (${hashes.joinToString("/") { it.first }})")
    } else {
        val forced = igParseHex(certOverride)
        if (!forced.contentEquals(apkCert)) {
            logger.warn("WARNING: -PintegrityCertSha256 differs from this APK's signer. " +
                "Correct for Play App Signing; this local APK will then FAIL on-device (by design).")
        }
        IgHashes(dex, so, forced, "-PintegrityCertSha256 override")
    }
}

fun igRenderHeader(h: IgHashes): String {
    val rnd = SecureRandom()
    fun rand32() = ByteArray(32).also { rnd.nextBytes(it) }
    fun arr(b: ByteArray) = b.joinToString(", ", "{ ", " }") { "0x%02x".format(it.toInt() and 0xFF) }
    fun split(v: ByteArray?): Pair<ByteArray, ByteArray> {
        val a = rand32()
        // Unused slots get pure noise in both shares => never decodes to a real hash.
        val b = if (v == null) rand32() else ByteArray(32) { i -> (a[i].toInt() xor v[i].toInt()).toByte() }
        return a to b
    }
    val cert = split(h.cert)
    val dex = (0 until igMaxDex).map { split(h.dex.getOrNull(it)?.second) }
    val so = igAbis.map { split(h.so[it]) }
    fun grid(rows: List<ByteArray>) = rows.joinToString(", \\\n    ", "{ \\\n    ", " \\\n}") { arr(it) }
    return buildString {
        appendLine("// ============================================================================")
        appendLine("// expected_hashes.h — AUTO-GENERATED by :app:printReleaseChecksums")
        appendLine("// Generated: ${Instant.now()}   Cert source: ${h.certSource}")
        appendLine("// expected = SHARE_A ^ SHARE_B (fresh random shares every run).")
        appendLine("// The IG-EXPECT lines are comments (not compiled) used by verifyReleaseChecksums.")
        appendLine("// ============================================================================")
        appendLine("// IG-EXPECT cert=${igHex(h.cert)}")
        h.dex.forEachIndexed { i, (n, v) -> appendLine("// IG-EXPECT dex$i=${igHex(v)}  ($n)") }
        h.so.forEach { (abi, v) -> appendLine("// IG-EXPECT so.$abi=${igHex(v)}") }
        appendLine("#pragma once")
        appendLine()
        appendLine("#define IG_DEX_COUNT ${h.dex.size}")
        appendLine()
        appendLine("#define IG_CERT_A ${arr(cert.first)}")
        appendLine("#define IG_CERT_B ${arr(cert.second)}")
        appendLine()
        appendLine("#define IG_DEX_A ${grid(dex.map { it.first })}")
        appendLine("#define IG_DEX_B ${grid(dex.map { it.second })}")
        appendLine()
        appendLine("// ABI slot order: ${igAbis.joinToString()}")
        appendLine("#define IG_SO_A ${grid(so.map { it.first })}")
        appendLine("#define IG_SO_B ${grid(so.map { it.second })}")
    }
}

fun igPrint(h: IgHashes, apk: File) {
    println("\n================ Integrity checksums ================")
    println("APK            : ${apk.path}")
    println("Signing cert   : ${igColonHex(h.cert)}   [${h.certSource}]")
    h.dex.forEach { (n, v) -> println("%-15s: %s".format(n, igHex(v))) }
    h.so.forEach { (abi, v) -> println("%-15s: %s".format("so[$abi]", igHex(v))) }
    println("=====================================================\n")
}

fun igHeaderFile(): File =
    (findProperty("integrityHeader") as String?)?.let { file(it) } ?: file("src/main/cpp/expected_hashes.h")

fun igApkFile(): File =
    (findProperty("integrityApk") as String?)?.let { file(it) }
        ?: layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile

tasks.register("printReleaseChecksums") {
    group = "integrity"
    description = "Computes dex/.so/cert SHA-256 from a signed release APK and writes expected_hashes.h"
    notCompatibleWithConfigurationCache("reads arbitrary files at execution time")
    mustRunAfter(tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" })
    doLast {
        val apk = igApkFile()
        val h = igCompute(apk, findProperty("integrityCertSha256") as String?)
        igPrint(h, apk)
        val header = igRenderHeader(h)
        if ((findProperty("integrityWriteHeader") as String?) == "false") {
            println("---- paste into app/src/main/cpp/expected_hashes.h ----\n$header")
        } else {
            val out = igHeaderFile()
            out.writeText(header)
            println("Wrote ${out.path}\nNEXT: rebuild the release (assembleRelease / bundleRelease), then run verifyReleaseChecksums.")
        }
    }
}

tasks.register("verifyReleaseChecksums") {
    group = "integrity"
    description = "Fails if the release APK's hashes don't match expected_hashes.h"
    notCompatibleWithConfigurationCache("reads arbitrary files at execution time")
    mustRunAfter(tasks.matching { it.name == "assembleRelease" || it.name == "bundleRelease" })
    doLast {
        val apk = igApkFile()
        val header = igHeaderFile()
        val expect = header.readLines()
            .mapNotNull { Regex("^// IG-EXPECT (\\S+)=([0-9a-f]{64})").find(it) }
            .associate { it.groupValues[1] to it.groupValues[2] }
        if (expect.isEmpty()) throw GradleException("${header.path} is unprovisioned. Run printReleaseChecksums first.")
        val certOverride = (findProperty("integrityCertSha256") as String?) ?: expect["cert"]
        val h = igCompute(apk, certOverride)
        igPrint(h, apk)
        val actual = buildMap {
            put("cert", igHex(h.cert))
            h.dex.forEachIndexed { i, (_, v) -> put("dex$i", igHex(v)) }
            h.so.forEach { (abi, v) -> put("so.$abi", igHex(v)) }
        }
        val mismatches = (expect.keys + actual.keys).filter { expect[it] != actual[it] }
        if (mismatches.isNotEmpty()) {
            throw GradleException(
                "Integrity MISMATCH for: $mismatches\n" +
                    mismatches.joinToString("\n") { "  $it expected=${expect[it]} actual=${actual[it]}" } +
                    "\nIf only dex/so differ, the build is non-deterministic or code changed after printReleaseChecksums."
            )
        }
        println("verifyReleaseChecksums: OK — ${actual.size} hashes match. This APK is tamper-locked.")
    }
}
