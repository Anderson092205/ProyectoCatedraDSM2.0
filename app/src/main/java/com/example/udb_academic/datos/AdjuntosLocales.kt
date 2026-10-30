package com.example.udb_academic.datos

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.udb_academic.modelo.Adjunto
import org.json.JSONArray
import java.io.File
import java.util.concurrent.Executors

class AdjuntosLocales(contexto: Context, private val estudianteId: String) {
    private val contexto = contexto.applicationContext
    private val preferencias = this.contexto.getSharedPreferences("adjuntos_$estudianteId", Context.MODE_PRIVATE)
    private val carpeta = File(this.contexto.filesDir, "adjuntos/$estudianteId").canonicalFile

    init { require(Regex("^[A-Za-z0-9_-]+$").matches(estudianteId)) { "La cuenta no tiene un identificador válido." } }

    /** Recupera los adjuntos privados asociados a un apunte en este dispositivo. */
    fun listar(apunteId: String): List<Adjunto> {
        validarId(apunteId)
        val registros = JSONArray(preferencias.getString(apunteId, "[]"))
        return (0 until registros.length()).map { indice ->
            val registro = registros.getJSONObject(indice)
            Adjunto(registro.getString("nombre"), registro.getString("ruta"), registro.getString("tipo"), registro.getLong("tamano"))
        }
    }

    /** Comprueba que un identificador no pueda salir de las carpetas de la cuenta. */
    private fun validarId(id: String) { require(Regex("^[A-Za-z0-9_-]+$").matches(id)) { "El apunte tiene un identificador inválido." } }

    /** Resuelve una ruta local y rechaza el acceso a archivos de otras carpetas. */
    private fun resolver(ruta: String): File {
        require(Regex("^local:[A-Za-z0-9_-]+/[A-Za-z0-9-]+$").matches(ruta)) { "La ruta del archivo no es válida." }
        val archivo = File(carpeta, ruta.removePrefix("local:")).canonicalFile
        require(archivo.path.startsWith(carpeta.path + File.separator)) { "El archivo no pertenece a esta cuenta." }
        return archivo
    }

    companion object {
        private val operaciones = Executors.newSingleThreadExecutor()
        private val principal = Handler(Looper.getMainLooper())

        /** Ejecuta una operación de archivos fuera del hilo de la interfaz y devuelve su resultado. */
        fun <T> ejecutar(operacion: () -> T, alTerminar: (T) -> Unit, alFallar: (Exception) -> Unit) {
            operaciones.execute {
                try { val resultado = operacion(); principal.post { alTerminar(resultado) } }
                catch (fallo: Exception) { principal.post { alFallar(fallo) } }
            }
        }
    }
}
