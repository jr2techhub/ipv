package com.ipv.pos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ipv.pos.data.db.UserEntity
import com.ipv.pos.domain.UserRole
import com.ipv.pos.ui.screens.AuthViewModel
import com.ipv.pos.ui.screens.IpvVmFactory
import com.ipv.pos.ui.screens.LoginScreen
import com.ipv.pos.ui.screens.PlaceholderScreen

/**
 * Grafo completo de las 15 pantallas del MVP (spec §3), vacío pero conectado.
 * Home muestra accesos rápidos filtrados por rol (matriz de permisos spec §2).
 */
object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val POS = "pos"
    const val CHECKOUT = "checkout"
    const val CASH = "cash"
    const val INVENTORY = "inventory"
    const val PRODUCT_EDIT = "product_edit"
    const val STOCK_MOVE = "stock_move"
    const val CUSTOMERS = "customers"
    const val INVOICES = "invoices"
    const val REPORTS = "reports"
    const val USERS = "users"
    const val SETTINGS = "settings"
    const val BACKUP_LICENSE = "backup_license"
    const val AUDIT = "audit"
}

@Composable
fun IpvApp() {
    val nav = rememberNavController()
    Scaffold { inner ->
        NavHost(navController = nav, startDestination = Routes.LOGIN, modifier = Modifier.padding(inner)) {
            composable(Routes.LOGIN) {
                val vm: AuthViewModel = viewModel(factory = IpvVmFactory.factory)
                LoginScreen(vm)
                if (vm.loggedInUser != null) {
                    // Navegación tras login exitoso
                    LaunchedEffectLogin(vm) { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } }
                }
            }
            composable(Routes.HOME) {
                val vm: AuthViewModel = viewModel(factory = IpvVmFactory.factory)
                HomeScreen(vm.loggedInUser ?: return@composable, onOpen = { route -> nav.navigate(route) })
            }
            // Sprints 1–5 reemplazan placeholders sin tocar el grafo:
            composable(Routes.POS) { PlaceholderScreen("POS / Venta") }
            composable(Routes.CHECKOUT) { PlaceholderScreen("Cobro") }
            composable(Routes.CASH) { PlaceholderScreen("Gestión de caja") }
            composable(Routes.INVENTORY) { PlaceholderScreen("Inventario") }
            composable(Routes.PRODUCT_EDIT) { PlaceholderScreen("Producto (alta/edición)") }
            composable(Routes.STOCK_MOVE) { PlaceholderScreen("Movimiento de inventario") }
            composable(Routes.CUSTOMERS) { PlaceholderScreen("Clientes") }
            composable(Routes.INVOICES) { PlaceholderScreen("Facturas") }
            composable(Routes.REPORTS) { PlaceholderScreen("Reportes") }
            composable(Routes.USERS) { PlaceholderScreen("Usuarios") }
            composable(Routes.SETTINGS) { PlaceholderScreen("Ajustes del negocio") }
            composable(Routes.BACKUP_LICENSE) { PlaceholderScreen("Respaldo y licencia") }
            composable(Routes.AUDIT) { PlaceholderScreen("Auditoría") }
        }
    }
}

@Composable
private fun LaunchedEffectLogin(vm: AuthViewModel, action: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(vm.loggedInUser) { if (vm.loggedInUser != null) action() }
}

/** Accesos rápidos según rol — la matriz se aplica también en dominio (no solo UI). */
@Composable
fun HomeScreen(user: UserEntity, onOpen: (String) -> Unit) {
    val role = UserRole.valueOf(user.role)
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Hola, ${user.name}", style = MaterialTheme.typography.headlineSmall)
        Text(roleLabel(role), style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onOpen(Routes.POS) }) { Text("Vender") }
            Button(onClick = { onOpen(Routes.CASH) }) { Text("Caja") }
        }
        if (role != UserRole.CASHIER) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(Routes.INVENTORY) }) { Text("Inventario") }
                Button(onClick = { onOpen(Routes.REPORTS) }) { Text("Reportes") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(Routes.CUSTOMERS) }) { Text("Clientes") }
                Button(onClick = { onOpen(Routes.INVOICES) }) { Text("Facturas") }
            }
        }
        if (role == UserRole.ADMIN) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(Routes.USERS) }) { Text("Usuarios") }
                Button(onClick = { onOpen(Routes.AUDIT) }) { Text("Auditoría") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpen(Routes.SETTINGS) }) { Text("Ajustes") }
                Button(onClick = { onOpen(Routes.BACKUP_LICENSE) }) { Text("Respaldo/Licencia") }
            }
        }
    }
}

private fun roleLabel(r: UserRole) = when (r) {
    UserRole.ADMIN -> "Administrador"
    UserRole.MANAGER -> "Gerente"
    UserRole.CASHIER -> "Cajero"
}
