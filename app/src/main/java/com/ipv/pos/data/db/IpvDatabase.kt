package com.ipv.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Esquema Room v1 = DDL completo de la spec §5 + adopciones ADR-001
 * (products.parent_id, payments.exchange_rate, invoice_items, indices extra).
 * Las vistas v_stock/v_receivables se implementan como consultas DAO equivalentes
 * (más control sobre índices y paginación en gama baja; documentado en ADR).
 */
@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        SupplierEntity::class,
        CustomerEntity::class,
        StockMovementEntity::class,
        CashSessionEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        InvoiceEntity::class,
        InvoiceItemEntity::class,
        FolioCounterEntity::class,
        UserEntity::class,
        AuditLogEntity::class,
        OutboxEntity::class,
        SettingEntity::class,
        SchemaMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class IpvDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun stockDao(): StockDao
    abstract fun salesDao(): SalesDao
    abstract fun cashSessionDao(): CashSessionDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun usersDao(): UsersDao
    abstract fun systemDao(): SystemDao

    companion object {
        const val NAME = "ipv_pos.db"
    }
}
