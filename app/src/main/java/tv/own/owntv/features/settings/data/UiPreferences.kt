package tv.own.owntv.features.settings.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.uiPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_ui_prefs")

/**
 * App-layer, presentation-only preferences.
 *
 * These live here rather than in core's `SettingsRepository` on purpose: they change only what the
 * UI *draws*, never how content is fetched, matched or stored, and they are not part of a profile
 * backup. Core owns the data model; the app owns how it is shown. A flag that gates a Compose row
 * has no business travelling in a backup file to another device with a different screen.
 */
class UiPreferences(private val context: Context) {
    private object Keys {
        val SHOW_CAST = booleanPreferencesKey("show_cast")
    }

    /**
     * Whether the cast/actor row (photos + names) is drawn on the film and series detail surfaces.
     * Defaults on — the metadata is already fetched, so this only hides an existing row.
     */
    val showCast: Flow<Boolean> = context.uiPrefsDataStore.data.map { it[Keys.SHOW_CAST] ?: true }

    suspend fun setShowCast(value: Boolean) {
        context.uiPrefsDataStore.edit { it[Keys.SHOW_CAST] = value }
    }
}
