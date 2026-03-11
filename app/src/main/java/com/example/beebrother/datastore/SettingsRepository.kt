import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.example.beebrother.datastore.AppConfig
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream

object AppConfigSerializer : Serializer<AppConfig> {
    override val defaultValue: AppConfig = AppConfig()

    override suspend fun readFrom(input: InputStream): AppConfig {
        try {
            return Json.decodeFromString(
                AppConfig.serializer(), input.readBytes().decodeToString()
            )
        } catch (exception: SerializationException) {
            throw CorruptionException("Cannot read AppConfig.", exception)
        }
    }

    override suspend fun writeTo(t: AppConfig, output: OutputStream) {
        output.write(
            Json.encodeToString(AppConfig.serializer(), t).encodeToByteArray()
        )
    }
}

private val Context.settingsDataStore by dataStore(
    fileName = "app_config.json",
    serializer = AppConfigSerializer
)

class SettingsRepository(private val context: Context) {
    val configFlow = context.settingsDataStore.data

    suspend fun updateSettings(update: (AppConfig) -> AppConfig) {
        context.settingsDataStore.updateData { currentConfig ->
            update(currentConfig)
        }
    }
}