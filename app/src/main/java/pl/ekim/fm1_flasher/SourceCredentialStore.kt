package pl.ekim.fm1_flasher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Private encrypted records; the encryption key never leaves Android Keystore. */
class SourceCredentialStore(context: Context, name: String = "catalog-credentials") {
    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val alias = "${context.packageName}.$name.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    internal fun readOpaque(id: String): String? = synchronized(LOCK) {
        val stored = preferences.getString(id, null) ?: return@synchronized null
        try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            require(bytes.size in 28..132000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(id.toByteArray())
            cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        } catch (_: Exception) { failed(msg("auth_storage_error")) }
    }
    internal fun writeOpaque(id: String, value: String): Unit = synchronized(LOCK) {
        try {
            val plain = value.toByteArray(); require(plain.size <= 128000)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key()); cipher.updateAAD(id.toByteArray())
            check(cipher.iv.size == 12)
            val encrypted = Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
            check(preferences.edit().putString(id, encrypted).commit())
        } catch (_: Exception) { failed(msg("auth_storage_error")) }
    }
    fun read(id: String): HttpCredentials? = synchronized(LOCK) {
        readOpaque(id)?.let { raw ->
            try {
                val json = JSONObject(raw)
                HttpCredentials(HttpAuthMethod.valueOf(json.getString("method")), json.getString("username"), json.getString("secret"), json.getString("header"),
                    oauthFromJson(json.optJSONObject("oauth")), json.optString("clientSecret"), json.optString("refreshToken"), json.optLong("expiresAt")).also { it.validate() }
            } catch (_: Exception) { failed(msg("auth_storage_error")) }
        }
    }
    internal fun readStamped(id: String): Pair<HttpCredentials, String>? = synchronized(LOCK) {
        val version = preferences.getString(id, null) ?: return@synchronized null
        read(id)!! to version
    }
    fun write(id: String, credentials: HttpCredentials): Unit = synchronized(LOCK) {
        credentials.validate()
        if (credentials.method == HttpAuthMethod.NONE) { remove(id); return@synchronized }
        writeOpaque(id, JSONObject().put("method", credentials.method.name).put("username", credentials.username)
            .put("secret", credentials.secret).put("header", credentials.header).put("oauth", oauthToJson(credentials.oauth))
            .put("clientSecret", credentials.clientSecret).put("refreshToken", credentials.refreshToken).put("expiresAt", credentials.expiresAt).toString())
    }
    internal fun writeIfUnchanged(id: String, version: String, credentials: HttpCredentials): Unit = synchronized(LOCK) {
        check(preferences.getString(id, null) == version) { failed(msg("source_changed")) }
        write(id, credentials)
    }
    fun remove(id: String): Unit = synchronized(LOCK) {
        check(preferences.edit().remove(id).commit()) { failed(msg("auth_storage_error")) }
    }
    companion object { private val LOCK = Any() }
}
