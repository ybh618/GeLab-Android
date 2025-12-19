package com.gelabzero.app

import android.app.Application
import com.gelabzero.app.agent.AgentController

class AgentApp : Application() {
    lateinit var agentController: AgentController
        private set

    override fun onCreate() {
        super.onCreate()
        agentController = AgentController(this)
    }
}
