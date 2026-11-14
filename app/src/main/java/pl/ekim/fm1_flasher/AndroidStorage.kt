package pl.ekim.fm1_flasher

import java.io.File

internal class AndroidStorage(private val directory: File, private val libraryOnly: Boolean = false) : AppStorage {
    private fun file(path: String): File {
        require(!libraryOnly || path.startsWith("library/"))
        val relative = if (libraryOnly) path.removePrefix("library/") else path
        val result = File(directory, relative).canonicalFile
        require(result.path.startsWith(directory.canonicalPath + File.separator))
        return result
    }
    override fun read(path: String, limit: Int): ByteArray? {
        if (libraryOnly && !path.startsWith("library/")) return null
        val file = file(path)
        if (!file.exists()) return null
        check(file.length() <= limit)
        return file.readBytes().also { check(it.size <= limit) }
    }
    override fun write(path: String, bytes: ByteArray) = LocalLibrary.atomicWrite(file(path), bytes)
    override fun remove(path: String) { val file = file(path); check(!file.exists() || file.delete()) { failed(msg(R.string.local_remove_error)) } }
    override fun size(path: String): Long? = file(path).takeIf { it.isFile }?.length()
    override fun list(path: String): List<String> = file(path).listFiles().orEmpty().map { it.name }
}
