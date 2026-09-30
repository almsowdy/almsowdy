package com.miro.education.admin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity:ComponentActivity(){
    private lateinit var vm:MiroAdminViewModel
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store=SessionStore(this)
        vm=MiroAdminViewModel(MiroRepository(this,store))
        setContent{MiroAdminApp(vm)}
    }
}