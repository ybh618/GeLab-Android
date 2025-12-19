package com.gelabzero.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gelabzero.app.agent.AgentConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.agentDataStore: DataStore<Preferences> by preferencesDataStore("agent_prefs")

class AgentPreferences(context: Context) {
    private val dataStore = context.agentDataStore

    val configFlow: Flow<AgentConfig> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { prefs ->
            AgentConfig(
                apiBase = prefs[Keys.API_BASE].orEmpty(),
                apiKey = prefs[Keys.API_KEY].orEmpty(),
                model = prefs[Keys.MODEL] ?: AgentConfig.DEFAULT_MODEL,
                instruction = prefs[Keys.INSTRUCTION].orEmpty(),
            )
        }

    suspend fun updateConfig(config: AgentConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.API_BASE] = config.apiBase
            prefs[Keys.API_KEY] = config.apiKey
            prefs[Keys.MODEL] = config.model
            prefs[Keys.INSTRUCTION] = config.instruction
        }
    }

    private object Keys {
        val API_BASE = stringPreferencesKey("api_base")
        val API_KEY = stringPreferencesKey("api_key")
        val MODEL = stringPreferencesKey("model")
        val INSTRUCTION = stringPreferencesKey("instruction")
    }
}
