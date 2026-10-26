package com.ipv.pos.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Login (Sprint 0): lista de usuarios activos + teclado PIN numérico.
 * Onboarding primer arranque: si no hay usuarios → alta del ADMIN inicial.
 */
@Composable
fun LoginScreen(
    vm: AuthViewModel = viewModel(factory = IpvVmFactory.factory),
) {
    LaunchedEffect(Unit) { vm.refreshUsers() }

    when {
        vm.needsBootstrap -> BootstrapAdminForm(vm)
        else -> LoginForm(vm)
    }
}

@Composable
private fun BootstrapAdminForm(vm: AuthViewModel) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Bienvenido a IPV", style = MaterialTheme.typography.headlineMedium)
        Text("Crea el usuario administrador del negocio", style = MaterialTheme.typography.bodyMedium)
        var name by remember { mutableStateOf("") }
        var pin by remember { mutableStateOf("") }
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre del dueño/admin") },
            modifier = Modifier.padding(top = 16.dp).fillMaxWidth())
        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
            label = { Text("PIN de 4 a 6 dígitos") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth())
        vm.bootstrapError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { vm.bootstrap(name, pin) }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Comenzar")
        }
    }
}

@Composable
private fun LoginForm(vm: AuthViewModel) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("IPV — Inventario y Punto de Venta", style = MaterialTheme.typography.titleLarge)
        Text("Selecciona tu usuario", style = MaterialTheme.typography.bodyMedium)
        vm.users.forEach { u ->
            Button(onClick = { vm.selectUser(u.id, u.name) }, modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()) {
                Text(u.name)
            }
        }
        if (vm.selectedUserId != null) {
            var pin by remember { mutableStateOf("") }
            OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.padding(top = 12.dp).fillMaxWidth())
            vm.loginMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { vm.login(pin) }, modifier = Modifier.padding(top = 12.dp)) { Text("Entrar") }
        }
    }
}

@Composable
fun PlaceholderScreen(title: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text("En construcción — Sprint siguiente", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
