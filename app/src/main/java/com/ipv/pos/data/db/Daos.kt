package com.ipv.pos.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    // categories
    @Insert suspend fun insertCategory(e: CategoryEntity)
    @Update suspend fun updateCategory(e: CategoryEntity)
    @Query("SELECT * FROM categories WHERE deleted = 0 ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    // products
    @Insert suspend fun insertProduct(e: ProductEntity)
    @Update suspend fun updateProduct(e: ProductEntity)
    @Query("SELECT * FROM products WHERE deleted = 0 AND active = 1 AND parent_id IS NULL ORDER BY name LIMIT :limit OFFSET :offset")
    fun observeProductsPage(offset: Int, limit: Int): Flow<List<ProductEntity>>
    @Query("""
        SELECT * FROM products WHERE deleted = 0 AND (name LIKE '%' || :q || '%' OR sku LIKE '%' || :q || '%' OR barcode = :q)
        ORDER BY name LIMIT 50
    """)
    fun searchProducts(q: String): Flow<List<ProductEntity>>
    @Query("SELECT * FROM products WHERE parent_id = :parentId AND deleted = 0 ORDER BY name")
    fun observeVariants(parentId: String): Flow<List<ProductEntity>>
    @Query("SELECT COUNT(*) FROM products WHERE deleted = 0 AND is_service = 0 AND parent_id IS NULL")
    suspend fun countSellableProducts(): Int

    // suppliers / customers
    @Insert suspend fun insertSupplier(e: SupplierEntity)
    @Update suspend fun updateSupplier(e: SupplierEntity)
    @Insert suspend fun insertCustomer(e: CustomerEntity)
    @Update suspend fun updateCustomer(e: CustomerEntity)
    @Query("SELECT * FROM customers WHERE deleted = 0 ORDER BY name")
    fun observeCustomers(): Flow<List<CustomerEntity>>
}

@Dao
interface StockDao {
    @Insert suspend fun insertMovement(e: StockMovementEntity)

    /** Vista v_stock materializada por consulta (equivalente, aprovecha idx_mov_product_date). */
    @Query("SELECT product_id, SUM(quantity) AS current_stock FROM stock_movements WHERE deleted = 0 GROUP BY product_id")
    fun observeAllStock(): Flow<List<StockRow>>

    @Query("SELECT COALESCE(SUM(quantity),0) FROM stock_movements WHERE product_id = :productId AND deleted = 0")
    suspend fun stockOf(productId: String): Double

    @Query("SELECT COALESCE(SUM(quantity),0) FROM stock_movements WHERE product_id = :productId AND deleted = 0")
    fun observeStockOf(productId: String): Flow<Double>

    @Query("SELECT * FROM stock_movements WHERE product_id = :productId AND deleted = 0 ORDER BY occurred_at DESC LIMIT 200")
    fun observeMovementHistory(productId: String): Flow<List<StockMovementEntity>>
}

@Dao
interface SalesDao {
    @Insert suspend fun insertSale(e: SaleEntity)
    @Update suspend fun updateSale(e: SaleEntity)
    @Insert suspend fun insertItems(items: List<SaleItemEntity>)
    @Insert suspend fun insertPayment(e: PaymentEntity)

    @Query("SELECT * FROM sales WHERE status = 'OPEN' ORDER BY created_at DESC LIMIT 1")
    suspend fun lastOpenSale(): SaleEntity?

    @Query("SELECT * FROM sales WHERE session_id = :sid AND status != 'OPEN' ORDER BY sold_at DESC")
    fun observeSalesBySession(sid: String): Flow<List<SaleEntity>>

    @Query("SELECT COALESCE(SUM(p.amount * COALESCE(p.exchange_rate, 1.0)),0) FROM payments p JOIN sales s ON p.sale_id = s.id
            WHERE s.session_id = :sid AND s.status = 'COMPLETED' AND p.method IN ('CASH_CUP','CASH_MLC','CASH_USD')")
    suspend fun cashInSession(sid: String): Double

    @Query("SELECT COALESCE(SUM(total),0) FROM sales WHERE customer_id = :cid AND status = 'COMPLETED'")
    suspend fun completedTotalForCustomer(cid: String): Double
}

@Dao
interface CashSessionDao {
    @Insert suspend fun insert(e: CashSessionEntity)
    @Update suspend fun update(e: CashSessionEntity)
    @Query("SELECT * FROM cash_register_sessions WHERE status = 'OPEN' AND user_id = :uid LIMIT 1")
    suspend fun openSessionFor(uid: String): CashSessionEntity?
    @Query("SELECT * FROM cash_register_sessions WHERE status = 'OPEN' LIMIT 1")
    fun observeAnyOpen(): Flow<List<CashSessionEntity>>
    @Query("SELECT * FROM cash_register_sessions ORDER BY opened_at DESC LIMIT 100")
    fun observeHistory(): Flow<List<CashSessionEntity>>
}

@Dao
interface InvoiceDao {
    @Insert suspend fun insertInvoice(e: InvoiceEntity)
    @Update suspend fun updateInvoice(e: InvoiceEntity)
    @Insert suspend fun insertInvoiceItems(items: List<InvoiceItemEntity>)

    /**
     * Reserva de folio: incrementa el contador del dispositivo. La lectura-posterior-update
     * ocurre dentro de @Transaction en el repositorio (Room serializa transacciones de escritura),
     * garantizando atomicidad sin depender de RETURNING (SQLite < 3.35 en Android 7-9).
     */
    @Query("UPDATE folio_counters SET last_number = last_number + 1 WHERE device_prefix = :prefix")
    suspend fun bumpFolio(prefix: String)

    @Query("SELECT last_number FROM folio_counters WHERE device_prefix = :prefix")
    suspend fun currentFolioNumber(prefix: String): Long?

    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insertFolioCounter(e: FolioCounterEntity)
    @Query("SELECT * FROM folio_counters WHERE device_prefix = :prefix")
    suspend fun folioCounter(prefix: String): FolioCounterEntity?

    @Query("SELECT * FROM invoices ORDER BY issue_date DESC LIMIT 200")
    fun observeInvoices(): Flow<List<InvoiceEntity>>
}

@Dao
interface UsersDao {
    @Insert suspend fun insert(e: UserEntity)
    @Update suspend fun update(e: UserEntity)
    @Query("SELECT * FROM users WHERE deleted = 0 AND active = 1 ORDER BY name")
    fun observeActiveUsers(): Flow<List<UserEntity>>
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun byId(id: String): UserEntity?
    @Query("SELECT COUNT(*) FROM users WHERE deleted = 0 AND active = 1")
    suspend fun countActive(): Int
}

@Dao
interface SystemDao {
    @Insert suspend fun insertAudit(e: AuditLogEntity)
    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT 300")
    fun observeAudit(): Flow<List<AuditLogEntity>>

    @Insert suspend fun insertOutbox(e: OutboxEntity)
    @Query("SELECT COUNT(*) FROM outbox WHERE synced_at IS NULL")
    fun observePendingSyncCount(): Flow<Int>

    @Insert suspend fun upsertSetting(e: SettingEntity)
    @Query("SELECT value_json FROM settings WHERE key = :key")
    suspend fun setting(key: String): String?

    @Insert suspend fun upsertMeta(e: SchemaMetaEntity)
    @Query("SELECT value FROM schema_meta WHERE key = :key")
    suspend fun meta(key: String): String?
}
