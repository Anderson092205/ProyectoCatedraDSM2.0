package com.example.udb_academic.notificaciones

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.example.udb_academic.modelo.Actividad
import org.json.JSONArray
import org.json.JSONObject

object Recordatorios {
    private const val CANAL = "recordatorios_academicos"

    /** Crea el canal de avisos de entregas y evaluaciones. */
    fun crearCanal(contexto: Context) {
        if (Build.VERSION.SDK_INT >= 26) contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CANAL, "Recordatorios académicos", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Avisos antes de entregar actividades" })
    }

    /** Actualiza los recordatorios locales para las actividades pendientes del usuario activo. */
    fun programar(contexto: Context, actividades: List<Actividad>, usuarioId: String) {
        cancelar(contexto)
        val registros = JSONArray()
        actividades.filter { it.estado == "pendiente" && it.fechaEntrega - 86400000 > System.currentTimeMillis() }.forEach { actividad ->
            val registro = JSONObject().put("id", actividad.id).put("titulo", actividad.titulo).put("momento", actividad.fechaEntrega - 86400000).put("usuarioId", usuarioId)
            registros.put(registro)
            programarUno(contexto, registro)
        }
        contexto.getSharedPreferences("recordatorios", Context.MODE_PRIVATE).edit().putString("registros", registros.toString()).apply()
    }

    /** Cancela los avisos programados y borra las notificaciones de la sesión. */
    fun cancelar(contexto: Context) {
        val preferencias = contexto.getSharedPreferences("recordatorios", Context.MODE_PRIVATE)
        val registros = JSONArray(preferencias.getString("registros", "[]"))
        val alarmas = contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (indice in 0 until registros.length()) {
            val intento = intento(contexto, registros.getJSONObject(indice))
            alarmas.cancel(intento)
            intento.cancel()
        }
        preferencias.edit().remove("registros").apply()
        NotificationManagerCompat.from(contexto).cancelAll()
    }

    /** Restaura los recordatorios guardados después de reiniciar el teléfono. */
    fun restaurar(contexto: Context) {
        val registros = JSONArray(contexto.getSharedPreferences("recordatorios", Context.MODE_PRIVATE).getString("registros", "[]"))
        for (indice in 0 until registros.length()) {
            val registro = registros.getJSONObject(indice)
            if (registro.getLong("momento") > System.currentTimeMillis()) programarUno(contexto, registro)
        }
    }

    /** Programa una alarma aproximada respetando las restricciones de batería de Android. */
    private fun programarUno(contexto: Context, registro: JSONObject) {
        (contexto.getSystemService(Context.ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, registro.getLong("momento"), intento(contexto, registro))
    }

    /** Construye una intención única para cada actividad del estudiante. */
    private fun intento(contexto: Context, registro: JSONObject): PendingIntent = PendingIntent.getBroadcast(contexto, registro.getString("id").hashCode(), Intent(contexto, ReceptorRecordatorio::class.java).setAction("udb.${registro.getString("id")}").putExtra("id", registro.getString("id")).putExtra("titulo", registro.getString("titulo")).putExtra("usuarioId", registro.getString("usuarioId")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

class ReceptorRecordatorio : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Recordatorios.restaurar(context)
    }
}
