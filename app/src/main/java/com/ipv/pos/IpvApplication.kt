package com.ipv.pos

import android.app.Application
import com.ipv.pos.di.AppGraph

class IpvApplication : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}
