# ADR-001 — Decisiones iniciales de producto y técnica (Sprint 0 cerrado)

**Fecha:** 2026-10-27 · **Estado:** Aceptado · **Reemplaza a:** supuestos abiertos de `mvp-spec.md` y `sprint-plan.md`

Documento de decisiones vinculantes para iniciar la codificación del MVP. El equipo de desarrollo asume estas respuestas en lugar de esperar la validación de campo; si la validación con negocios reales contradice alguna, se abre un ADR nuevo (no se edita este).

---

## 1. Decisiones de producto (validación asumida)

### 1.1 Variantes de producto → SÍ, incluidas en el MVP (tabla mínima)
**Decisión:** los restaurantes/cafeterías (sector prioritario) no venden sin variantes (café pequeño/mediano/grande, pizza personal/familiar). Un POS que no las soporta no es vendable en Cuba.
**Diseño elegido: variante = producto hijo** (no tabla separada de precios por variante):
- Se añade a `products` la columna `parent_id TEXT REFERENCES products(id)` (NULL = producto normal o padre de variantes).
- La variante es un producto completo: tiene su propio `sku`, `barcode`, `sale_price`, `cost_price` y **su propio stock derivado de movimientos**. No requiere lógica nueva en ventas ni inventario.
- Reglas: un padre no se vende directamente (solo sus variantes); máximo 1 nivel de anidamiento (padre→variante); hasta 8 variantes por padre (suficiente y mantiene la grilla del POS legible en gama baja).
- El POS muestra el padre como "ficha expandible": al tocarla aparece la fila de variantes. Sin cambios en el esquema de `sale_items`/`stock_movements`.
- **Costo:** ~2 días extra en Sprint 1. Cero reestructuración del POS (Sprint 2 intacto).

### 1.2 Crédito / fiado → el diseño de la spec es correcto, sin cuotas
**Decisión:** mantener modelo simple: venta a CRÉDITO → saldo por cliente → abonos libres (`payments.sale_id IS NULL`) → reporte corriente/+30/+60. Límite opcional con override ADMIN.
**Descartado del MVP:** planillas de cuotas fijas mensuales, intereses por mora, recordatorios automáticos de cobro por SMS. Van a Fase 4 (retención/upsell).

### 1.3 Reportes → los 7 de la spec son el estándar mínimo vendable
Cierre diario · ventas por período · top productos · ventas por categoría · valuación de inventario (CPP) · cuentas por cobrar · resumen de impuestos.
**Descartado del MVP:** margen bruto por producto y comparativas entre períodos → Fase 3 (diferenciador PREMIUM).

### 1.4 Moneda → CUP como unidad contable única
Los pagos en MLC/USD/ML-cash se registran con `amount` en su moneda + tipo de cambio capturado manualmente en el momento del pago (campo `exchange_rate` añadido a `payments`). Toda la contabilidad, reportes y caja se consolidan en CUP. Sin conversión automática (no hay feed offline fiable).

### 1.5 Impuestos → IVA conceptual, configurable por datos
Tasa por defecto en settings; exentos incluidos. No se hardcodea ninguna regla ONAT: el país aún no las fija para MIPYMES/TCP. Los impuestos son **informativos** en el recibo/factura local.

### 1.6 Impresión → PDF/WhatsApp como canal principal; ESC/POS "experimental"
**Contrariedad al plan original:** no podemos comprar y probar impresoras físicas antes del Sprint 2 desde este entorno. Decisión: el recibo PDF compartido por WhatsApp cumple DoD del Sprint 2; la impresión Bluetooth ESC/POS (raw socket RFCOMM, comandos mínimos: init, texto, feed, cut) se implementa como módulo aislado y se marca "experimental" en UI hasta validarla con hardware real. Nunca bloquea una venta.

---

## 2. Decisiones técnicas (anclas de versiones)

| Parámetro | Decisión | Justificación |
|---|---|---|
| Estructura | **Monolito modular por paquetes** (`app` único módulo; paquetes `domain/data/ui/di`) | 1–2 devs, APK < 40 MB, builds rápidos; Clean Architecture se respeta por paquetes + reglas de dependencias. Migrar a multi-módulo sería fricción sin ganancia ahora. |
| Lenguaje/UI | Kotlin 2.0.x + Jetpack Compose BOM 2024.09.x (Material 3) | Estables, sin APIs experimentales inestables para nuestros patrones de pantalla. |
| AGP | 8.5.x · Gradle 8.7+ | Compatible con Kotlin 2.0/KSP. |
| minSdk / target | **minSdk 24** (Android 7.0) / target 34 | Amplía parque de dispositivos usables en Cuba (muchos Android 7–9 residuales) sin costo real: nada del stack exige API 26+. Spec decía 26; 24 es estrictamente mejor para el mercado objetivo. |
| BD | Room 2.6.x + SQLCipher 4.5.4 (android-database-sqlcipher) + key derivada con Android Keystore | Según arquitectura. |
| IDs | **UUIDv7** generado en dispositivo (implementación propia, ~30 líneas, sin librería) | Ordenable por tiempo = índices SQLite eficientes. |
| Fechas | epoch millis UTC (`Long`) en toda la BD; presentación en zona horaria local Cuba (America/Havana) | Evita ambigüedad con cambios de hora. |
| Dinero | `REAL` en SQLite según spec, pero **cálculo de dominio en `BigDecimal`/centavos enteros**; redondeo HALF_UP a 2 decimales solo al persistir totales | Cuadre al centavo (criterio de aceptación #3) exige evitar acumulación de error float. |
| DI | Manual (grafos de constructores por capas, sin Hilt) | Menos mágica, menos tamaño APK, suficiente para 1–2 devs. Revisitable con ADR. |
| Async | Coroutines + Flow; Room devuelve `Flow` para listas reactivas | Estándar, liviano. |
| Imágenes/escáner | ML Kit Barcode Scanning (bundled offline, modelo mínimo) | Funciona sin red, requisito duro. |
| PDF | `PdfDocument` (API nativa) — sin librerías externas | Cero peso extra. |
| Gráficos | **Canvas/Compose custom ligero en vez de MPAndroidChart** | MPAndroidChart está poco mantenido y suma ~2 MB; 2 gráficas simples (barras ventas/día, top productos) se hacen con Compose Canvas. |
| Tests | JUnit5 + Turbine + Robolectric (integración Room con SQLCipher en JVM) + pruebas de dominio puras | Meta: 100% cobertura en reglas de dinero/stock. |
| CI | GitHub Actions: build + unit/integration tests en cada push; artefacto APK en `main` | Barato y alineado con distribución por APK directo. |
| Idioma UI | Español (Cuba) como locale único en MVP; strings externalizados desde el día 1 | Mercado objetivo; i18n futura sin refactor. |

## 3. Alcance confirmado para empezar a codificar (Sprint 0 → código)

1. Proyecto Gradle completo (monolito, paquetes domain/data/ui).
2. Esquema Room v1 con **todas** las tablas/vistas de la spec **+** adopciones de este ADR: `products.parent_id`, `payments.exchange_rate`, `schema_meta`, migraciones versionadas.
3. Convenciones base: UUIDv7, campos comunes (created_at/updated_at/version/deleted/sync_state), DAO base, escritura al outbox en la misma transacción (se escribe desde el MVP aunque nadie lo consuma hasta Fase 2).
4. Navegación completa del grafo de 15 pantallas con placeholders.
5. Login: users + PIN hash (PBKDF2, salt por usuario) + bloqueo 5 intentos/5 min + auditoría LOGIN_FAIL. Onboarding primer arranque (crear ADMIN → perfil negocio).
6. Módulo de licencia: verificación Ed25519 offline (clave pública embebida), estado FREE por defecto. (Emisor de licencias = script Python aparte en `tools/license-issuer`.)

**Criterio de salida:** app compila, crea BD cifrada, ADMIN onboarda y loguea, modo avión 100% funcional.

---

## 4. Consecuencias
- Sprint 1 crece ~2 días (variantes); Sprint 2 pierde dependencia crítica (impresora pasa a experimental). Resto del cronograma de 6 sprints se mantiene.
- Si la validación de campo real rechaza variantes, desactivar `parent_id` es trivial (columna nullable ignorada por la UI).
- La decisión BigDecimal/centavos debe propagarse a todas las use cases de dinero desde el primer commit.
