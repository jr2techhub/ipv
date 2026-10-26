package com.ipv.pos.di

import android.content.Context
import androidx.room.Room
import com.ipv.pos.data.db.IpvDatabase
import com.ipv.pos.data.db.DatabaseKeyProvider
import com.ipv.pos.data.repo.AuditWriter
import com.ipv.pos.data.repo.AuthRepository
import com.ipv.pos.data.repo.OutboxWriter
import com.ipv.pos.data.repo.SettingsRepository

/**
 * DI manual por grafos de constructores (ADR-001 §2): sin Hilt, menos APK, explícito.
 * Singleton de DB con SQLCipher; lazy para no tocar Keystore en el hilo principal del test.
 */
class AppGraph(val context: Context) {

    val db: IpvDatabase by lazy {
        Room.databaseBuilder(context, IpvDatabase::class.java, IpvDatabase.NAME)
            .openHelperFactory(DatabaseKeyProvider.sqlCipherFactory(context))
            // v1 es la base: las migraciones versionadas (2→3→…) llegan cuando exista v2
            // (spec §4.7). Downgrade deliberadamente NO destructivo: mejor rechazar apertura
            // que perder datos del negocio ante una reinstalación de versión anterior.
            .build()
    }

    val outbox: OutboxWriter by lazy { OutboxWriter(db) }
    val audit: AuditWriter by lazy { AuditWriter(db) }
    val auth: AuthRepository by lazy { AuthRepository(db, outbox, audit) }
    val settings: SettingsRepository by lazy { SettingsRepository(db, context) }
}
