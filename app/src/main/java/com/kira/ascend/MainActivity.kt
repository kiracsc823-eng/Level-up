package com.kira.ascend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.kira.ascend.ui.AppRoot

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(vm) }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshDay()
    }
}
