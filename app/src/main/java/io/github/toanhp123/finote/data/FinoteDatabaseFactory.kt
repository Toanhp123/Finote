package io.github.toanhp123.finote.data

import android.content.Context
import androidx.room3.Room
import io.github.toanhp123.finote.data.security.DatabaseKeyStore
import io.github.toanhp123.finote.data.security.KeystoreKeyCipher
import io.github.toanhp123.finote.data.security.deletePlaintextDatabase
import io.github.toanhp123.finote.data.security.toRawKeyPassphrase
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.driver.SQLCipherDriver

const val DATABASE_NAME = "finote.db"

/** Opens the SQLCipher database. Old plaintext files and a lost key are handled before the driver sees the file. */
fun createFinoteDatabase(
    context: Context,
    name: String = DATABASE_NAME,
    keyStore: DatabaseKeyStore = DatabaseKeyStore(context, KeystoreKeyCipher(), name),
): FinoteDatabase {
    deletePlaintextDatabase(context, name)
    val passphrase = keyStore.getOrCreateKey().toRawKeyPassphrase()
    System.loadLibrary("sqlcipher")
    // The library logs to Logcat by default. The privacy rule has no debug exception.
    Logger.setTarget(NoopTarget())
    return Room
        .databaseBuilder<FinoteDatabase>(context, name)
        .setDriver(SQLCipherDriver(passphrase, null, null))
        .build()
}
