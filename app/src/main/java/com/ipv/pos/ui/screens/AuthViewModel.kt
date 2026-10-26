package com.ipv.pos.ui.screens

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ipv.pos.IpvApplication
import com.ipv.pos.data.db.UserEntity
import com.ipv.pos.data.repo.AuthRepository
import com.ipv.pos.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fábrica de VMs con acceso al AppGraph (DI manual sin Hilt). */
object IpvVmFactory {
    val factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            // Compose viewModel() no inyecta Application aquí; se resuelve vía cast del contexto global.
            val app = (IpvAppHolder.app) as Application
            return AuthViewModel(app) as T
        }
    }
}

/** Holder simple para DI manual (el Application se setea en MainActivity.onCreate). */
object IpvAppHolder { lateinit var app: Application }

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as IpvApplication).graph

    var users by mutableStateOf<List<UserEntity>>(emptyList())
        private set
    var needsBootstrap by mutableStateOf(false)
        private set
    var selectedUserId by mutableStateOf<String?>(null)
        private set
    var loginMessage by mutableStateOf<String?>(null)
        private set
    var bootstrapError by mutableStateOf<String?>(null)
        private set

    var loggedInUser by mutableStateOf<UserEntity?>(null)
        private set

    fun refreshUsers() {
        viewModelScope.launch {
            users = graph.db.usersDao().observeActiveUsers().first()
            needsBootstrap = users.isEmpty()
        }
    }

    fun selectUser(id: String, name: String) {
        selectedUserId = id
        loginMessage = null
    }

    fun bootstrap(name: String, pin: String) {
        viewModelScope.launch {
            bootstrapError = null
            graph.auth.bootstrapAdmin(name, pin)
                .onSuccess { loggedInUser = it; needsBootstrap = false; refreshUsers() }
                .onFailure { bootstrapError = it.message ?: "No se pudo crear el administrador" }
        }
    }

    fun login(pin: String) {
        val uid = selectedUserId ?: return
        viewModelScope.launch {
            loginMessage = null
            when (val r = graph.auth.login(uid, pin)) {
                is AuthRepository.LoginResult.Success -> loggedInUser = r.user
                AuthRepository.LoginResult.WrongPin -> loginMessage = "PIN incorrecto"
                is AuthRepository.LoginResult.Locked -> loginMessage = "Usuario bloqueado por intentos fallidos. Espera unos minutos."
                AuthRepository.LoginResult.UnknownUser -> { loginMessage = "Usuario no disponible"; refreshUsers() }
            }
        }
    }

    fun logout() { loggedInUser = null; selectedUserId = null; loginMessage = null }

    val role: UserRole? get() = loggedInUser?.role?.let { UserRole.valueOf(it) }
}
