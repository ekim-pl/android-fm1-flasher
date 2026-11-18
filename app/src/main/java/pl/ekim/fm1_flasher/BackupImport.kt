package pl.ekim.fm1_flasher

import java.io.InputStream
import java.util.zip.ZipInputStream
import org.json.JSONObject

internal object BackupImport {
    private const val META_LIMIT = 16384
    fun bounded(stream: InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            require(out.size().toLong() + count <= limit) { "Import exceeds size limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    fun read(raw: ByteArray, sidecar: ByteArray?, store: RecoveryStore): RecoveryBackup {
        var image = raw; var metadata = sidecar
        var dataOnly = false
        require(raw.size <= Recovery.FLASH_SIZE + 65536)
        if (raw.take(2) == listOf(0x50.toByte(), 0x4B.toByte())) {
            require(sidecar == null)
            val entries = mutableMapOf<String, ByteArray>()
            ZipInputStream(raw.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in setOf("full-flash-before.bin", "user-data.bin", "backup.json", "result.json") && entry.name !in entries) { "Unexpected or duplicate archive entry" }
                    entries[entry.name] = bounded(zip, if (entry.name.endsWith(".bin")) Recovery.FLASH_SIZE else META_LIMIT)
                    zip.closeEntry()
                }
            }
            dataOnly = "user-data.bin" in entries
            require(!dataOnly || "full-flash-before.bin" !in entries) { "Ambiguous backup payload" }
            image = checkNotNull(entries[if (dataOnly) "user-data.bin" else "full-flash-before.bin"]) { "Backup image missing" }
            metadata = checkNotNull(entries["backup.json"]) { "Backup metadata missing" }
        }
        require(if (dataOnly) image.size in UserDataBackup.MIN_SIZE..UserDataBackup.MAX_SIZE else image.size == Recovery.FLASH_SIZE) { "Invalid backup size" }
        val meta = metadata?.also { require(it.size <= META_LIMIT) }?.let { JSONObject(it.toString(Charsets.UTF_8)) }
        val expected = meta?.optString("sha256").orEmpty()
        if (expected.isNotEmpty()) require(expected.matches(Regex("[0-9a-fA-F]{64}")) && FirmwarePackage.sha256(image).equals(expected, true)) { "Backup checksum mismatch" }
        fun numeric(key: String, alternate: String, unknown: Long): Long {
            if (meta == null) return unknown
            if (meta.has(key)) return meta.getLong(key)
            val value = meta.optString(alternate).substringBefore(' ')
            return if (value.startsWith("0x")) value.drop(2).toLongOrNull(16) ?: error("Invalid metadata number") else unknown
        }
        if (dataOnly) require(meta?.optInt("schema") == 3 && expected.isNotEmpty())
        else if (meta?.has("schema") == true) require(meta.getInt("schema") in 1..2)
        if (meta?.has("size") == true) require(meta.getInt("size") == image.size)
        val id = numeric("flashId", "flash chip", 0)
        val keyValue = numeric("chipKey", "chip key", -1)
        require(id in 0..0xFFFFFF && keyValue in -1..65535)
        val key = keyValue.toInt()
        if (!dataOnly && meta?.has("bootHash") == true) require(meta.getString("bootHash") == FirmwarePackage.sha256(image.copyOfRange(0, Recovery.START))) { "Boot backup checksum mismatch" }
        val uid = (meta?.optString("uid")?.takeIf { it.isNotBlank() } ?: meta?.optString("unit id"))
            ?.lowercase()?.takeIf { it.matches(Regex("[0-9a-f]{16}")) && it != "0000000000000000" && it != "ffffffffffffffff" }
        return store.save(image, id, key, DeviceDiagnostics(id, key, Recovery.FLASH_SIZE, uid, transport = "IMPORT"),
            expected.isNotEmpty() && (meta?.optBoolean("verified", true) == true), dataOnly)
    }
}
