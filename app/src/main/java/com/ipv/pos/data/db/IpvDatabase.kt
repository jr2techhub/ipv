package com.ipv.pos.data.db
class IpvDatabase {
    val users = UsersDao()
    val system = SystemDao()
    fun usersDao(): UsersDao = users
    fun systemDao(): SystemDao = system
}
