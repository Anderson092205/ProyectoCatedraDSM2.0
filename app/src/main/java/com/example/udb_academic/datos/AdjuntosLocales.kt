package com.example.udb_academic.datos

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.example.udb_academic.modelo.Adjunto
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
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

    /** Copia un PDF o imagen al almacenamiento interno y guarda sus metadatos localmente. */
    fun guardar(apunteId: String, origen: Uri, nombre: String, tipo: String): Adjunto {
        validarId(apunteId)
        require(nombre.length in 1..180) { "El nombre del archivo admite hasta 180 caracteres." }
        require(tipo in listOf("application/pdf", "image/jpeg", "image/png", "image/webp")) { "Elegí una imagen o un documento PDF." }
        val anteriores = listar(apunteId)
        check(anteriores.size < 5) { "El apunte ya tiene cinco adjuntos." }
        val ruta = "local:$apunteId/${UUID.randomUUID()}"
        val archivo = resolver(ruta)
        check(archivo.parentFile?.mkdirs() == true || archivo.parentFile?.isDirectory == true) { "No se pudo preparar el almacenamiento del teléfono." }
        try {
            val entrada = contexto.contentResolver.openInputStream(origen) ?: error("No se pudo leer el archivo seleccionado.")
            entrada.use { lectura -> archivo.outputStream().use { salida ->
                val bloque = ByteArray(8192)
                var total = 0L
                while (true) {
                    val cantidad = lectura.read(bloque)
                    if (cantidad < 0) break
                    total += cantidad
                    require(total <= 10485760) { "El archivo supera el máximo de 10 MB." }
                    salida.write(bloque, 0, cantidad)
                }
                require(total > 0) { "El archivo está vacío." }
            } }
            val adjunto = Adjunto(nombre, ruta, tipo, archivo.length())
            guardarLista(apunteId, anteriores + adjunto)
            return adjunto
        } catch (fallo: Exception) { archivo.delete(); throw fallo }
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

    /** Persiste la lista local de adjuntos sin enviarla a Firestore. */
    private fun guardarLista(apunteId: String, adjuntos: List<Adjunto>) {
        val registros = JSONArray()
        adjuntos.forEach { adjunto -> registros.put(JSONObject().put("nombre", adjunto.nombre).put("ruta", adjunto.ruta).put("tipo", adjunto.tipo).put("tamano", adjunto.tamano)) }
        check(preferencias.edit().putString(apunteId, registros.toString()).commit()) { "No se pudo guardar la información del archivo." }
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
