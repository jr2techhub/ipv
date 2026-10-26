package com.ipv.pos

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.ipv.pos.ui.IpvApp
import com.ipv.pos.ui.screens.IpvAppHolder
import com.ipv.pos.ui.theme.IpvTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        IpvAppHolder.app = application as Application
        setContent {
            IpvTheme { IpvApp() }
        }
    }
}
