package com.ipv.pos.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Convenciones compartidas (spec §5): id UUIDv7, created_at/updated_at epoch ms UTC,
 * version incremental, deleted soft-delete, sync_state reservado Fase 2 pero escrito desde MVP.
 * Cada inserción/actualización de estas entidades encola además una fila en `outbox`
 * dentro de la MISMA transacción (repositorios lo garantizan).
 */

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parent_id: String?,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(
    tableName = "products",
    indices = [
        Index("barcode"), Index("name"), Index("category_id"), Index("parent_id"),
    ],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val sku: String?,
    val barcode: String?,
    val name: String,
    val category_id: String?,
    val unit: String = "u",
    val cost_price: Double = 0.0,          // costo promedio ponderado (se reescribe solo por lógica CPP)
    val sale_price: Double?,
    val tax_rate: Double = 0.0,            // fracción 0..1, data-driven
    val min_stock: Double = 0.0,
    val is_service: Int = 0,
    val parent_id: String? = null,         // ADR-001 §1.1: variante = producto hijo (máx 1 nivel)
    val active: Int = 1,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tax_id: String?,
    val contact: String?,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val tax_id: String?,                   // NIT o carnet
    val phone: String?,
    val email: String?,
    val credit_limit: Double?,             // NULL = sin límite definido
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

/** Append-only: el stock actual SIEMPRE se deriva (v_stock), nunca se fija desde UI. */
@Entity(
    tableName = "stock_movements",
    indices = [Index("product_id", "occurred_at"), Index("reference_type", "reference_id")],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val product_id: String,
    val type: String,                      // SALE|PURCHASE|ADJUSTMENT|RETURN|CREDIT_NOTE|TRANSFER
    val quantity: Double,                  // con signo: + entrada, - salida
    val unit_cost: Double = 0.0,
    val reference_type: String?,           // sale|purchase|manual|return
    val reference_id: String?,
    val reason: String?,                   // obligatorio en ADJUSTMENT (validado en use case)
    val user_id: String,
    val occurred_at: Long,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(tableName = "cash_register_sessions", indices = [Index("user_id"), Index("status")])
data class CashSessionEntity(
    @PrimaryKey val id: String,
    val user_id: String,
    val opened_at: Long,
    val closed_at: Long? = null,
    val opening_balance: Double = 0.0,
    val expected_balance: Double? = null,
    val counted_balance: Double? = null,
    val difference: Double? = null,
    val close_comment: String? = null,
    val status: String = "OPEN",           // OPEN|CLOSED
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(
    tableName = "sales",
    indices = [Index("sold_at"), Index("customer_id"), Index("session_id"), Index("status")],
)
data class SaleEntity(
    @PrimaryKey val id: String,
    val session_id: String,
    val invoice_id: String? = null,
    val customer_id: String? = null,
    val user_id: String,
    val subtotal: Double,
    val discount_total: Double = 0.0,
    val tax_total: Double,
    val total: Double,
    val status: String = "OPEN",           // OPEN|COMPLETED|VOID
    val void_reason: String? = null,
    val voided_by: String? = null,
    val voided_at: Long? = null,
    val sold_at: Long,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(tableName = "sale_items", indices = [Index("sale_id")])
data class SaleItemEntity(
    @PrimaryKey val id: String,
    val sale_id: String,
    val product_id: String,
    val product_name_snapshot: String,     // nombre al momento de la venta (inmutable ante ediciones)
    val quantity: Double,
    val unit_price: Double,
    val discount: Double = 0.0,
    val tax_rate: Double = 0.0,
    val tax_amount: Double = 0.0,
    val line_total: Double,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(
    tableName = "payments",
    indices = [Index("sale_id"), Index("customer_id")],
)
data class PaymentEntity(
    @PrimaryKey val id: String,
    val sale_id: String?,                  // NULL = abono directo a cuenta por cobrar
    val customer_id: String?,
    val method: String,                    // CASH_CUP|CASH_MLC|CASH_USD|CARD_MLC|TRANSFER|CREDIT
    val amount: Double,                    // en la moneda del método
    val currency: String = "CUP",
    val exchange_rate: Double? = null,     // ADR-001 §1.4: capturado manualmente; contable = amount*rate en CUP
    val reference: String? = null,
    val user_id: String,
    val paid_at: Long,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(
    tableName = "invoices",
    indices = [Index(value = ["folio"], unique = true), Index("issue_date")],
)
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val folio: String,                     // prefijo_dispositivo + contador, nunca reutilizado
    val sale_id: String,
    val customer_id: String,
    val issue_date: Long,
    val status: String = "ISSUED",         // ISSUED|VOID|SENT_TO_AUTHORITY|REJECTED
    val subtotal: Double,
    val tax_total: Double,
    val total: Double,
    val pdf_path: String? = null,
    val qr_payload: String? = null,
    val authority_ack: String? = null,     // reservado ONAT Fase 3
    val void_reason: String? = null,
    val voided_at: Long? = null,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

@Entity(tableName = "invoice_items", indices = [Index("invoice_id")])
data class InvoiceItemEntity(
    @PrimaryKey val id: String,
    val invoice_id: String,
    val product_id: String,
    val product_name_snapshot: String,
    val quantity: Double,
    val unit_price: Double,
    val discount: Double = 0.0,
    val tax_rate: Double = 0.0,
    val tax_amount: Double = 0.0,
    val line_total: Double,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

/** Folio secuencial por dispositivo — columna PK device_prefix según spec. */
@Entity(tableName = "folio_counters")
data class FolioCounterEntity(
    @PrimaryKey val device_prefix: String,
    val last_number: Long = 0,
)

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val role: String,                      // ADMIN|MANAGER|CASHIER
    val pin_hash: String,                  // PBKDF2 "iter:salt:hash"
    val pin_salt: String,                  // hex separado para compatibilidad con DDL de spec
    val failed_attempts: Int = 0,
    val locked_until: Long? = null,
    val active: Int = 1,
    val created_at: Long,
    val updated_at: Long,
    val version: Int = 1,
    val deleted: Int = 0,
    val sync_state: String = "pending",
)

/** Append-only puro: sin updated_at/deleted/sync_state mutables (inmutable por diseño, spec §4.9). */
@Entity(tableName = "audit_log", indices = [Index("at")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val user_id: String?,
    val action: String,                    // VOID_SALE|ADJUST_STOCK|PRICE_CHANGE|RESTORE|LOGIN_FAIL|...
    val entity: String?,
    val entity_id: String?,
    val before_json: String?,
    val after_json: String?,
    val at: Long,
)

/** Cola de sincronización: se escribe desde el MVP (misma transacción), se consume en Fase 2. */
@Entity(
    tableName = "outbox",
    indices = [Index("synced_at")],
)
data class OutboxEntity(
    @PrimaryKey val id: String,
    val entity: String,
    val entity_id: String,
    val operation: String,                 // INSERT|UPDATE|DELETE
    val payload_json: String,
    val attempts: Int = 0,
    val last_error: String? = null,
    val queued_at: Long,
    val synced_at: Long? = null,
)

@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val key: String,           // business_profile|tax_config|app_settings|license_data|...
    val value_json: String,
    val updated_at: Long,
)

@Entity(tableName = "schema_meta")
data class SchemaMetaEntity(
    @PrimaryKey val key: String,           // schema_version|device_id|install_date|last_backup_at|last_sync_cursor
    val value: String,
)

/** Proyección de la vista v_stock (stock derivado, siempre SUM de movimientos). */
data class StockRow(
    @ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "current_stock") val currentStock: Double,
)
