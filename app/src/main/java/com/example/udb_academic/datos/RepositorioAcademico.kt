package com.example.udb_academic.datos

import android.net.Uri
import android.content.Context
import com.example.udb_academic.modelo.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import java.util.UUID

class RepositorioAcademico(private val contexto: Context, val demostracion: Boolean = false) {
    var estudiante = Estudiante()
    var materias = listOf<Materia>()
    var horarios = listOf<Horario>()
    var actividades = listOf<Actividad>()
    var apuntes = listOf<Apunte>()
    var noticias = listOf<Noticia>()
    var desdeCache = false
    var escriturasPendientes = false
    private val suscripciones = mutableListOf<ListenerRegistration>()
    private val estadosCache = mutableMapOf<String, Boolean>()
    private val estadosPendientes = mutableMapOf<String, Boolean>()
    var alCambiar: () -> Unit = {}
    var alFallar: (Exception) -> Unit = {}
    private val base get() = FirebaseFirestore.getInstance()
    private val identificador get() = FirebaseAuth.getInstance().currentUser?.uid ?: error("No hay una sesión activa.")
    private val almacenamientoLocal get() = AdjuntosLocales(contexto, if (demostracion) "demostracion" else identificador)
    private val documentoEstudiante get() = base.collection("estudiantes").document(identificador)

    /** Suscribe los datos del estudiante y las noticias publicadas a cambios en tiempo real. */
    fun escuchar() {
        detener()
        if (demostracion) { if (materias.isEmpty()) cargarEjemplo(); apuntes = apuntes.map { it.copy(adjuntos = almacenamientoLocal.listar(it.id)) }; alCambiar(); return }
        suscripciones += documentoEstudiante.addSnapshotListener { documento, error ->
            if (error != null) { alFallar(error); return@addSnapshotListener }
            estudiante = documento?.toObject(Estudiante::class.java) ?: Estudiante(correo = FirebaseAuth.getInstance().currentUser?.email.orEmpty())
            alCambiar()
        }
        observar("materias", Materia::class.java) { materias = it }
        observar("horarios", Horario::class.java) { horarios = it }
        observar("actividades", Actividad::class.java) { actividades = it }
        observar("apuntes", Apunte::class.java) { registros -> apuntes = registros.map { it.copy(adjuntos = almacenamientoLocal.listar(it.id)) } }
        suscripciones += base.collection("noticias").orderBy("fecha", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(50).addSnapshotListener { consulta, error ->
            if (error != null) { alFallar(error); return@addSnapshotListener }
            noticias = consulta?.documents?.mapNotNull { it.toObject(Noticia::class.java)?.copy(id = it.id) }.orEmpty()
            alCambiar()
        }
    }

    /** Convierte cada documento de una colección privada en su modelo correspondiente. */
    private fun <T : Any> observar(coleccion: String, clase: Class<T>, asignar: (List<T>) -> Unit) {
        suscripciones += documentoEstudiante.collection(coleccion).addSnapshotListener(MetadataChanges.INCLUDE) { consulta, error ->
            if (error != null) { alFallar(error); return@addSnapshotListener }
            estadosCache[coleccion] = consulta?.metadata?.isFromCache == true
            estadosPendientes[coleccion] = consulta?.metadata?.hasPendingWrites() == true
            desdeCache = estadosCache.values.any { it }
            escriturasPendientes = estadosPendientes.values.any { it }
            asignar(consulta?.documents?.mapNotNull { it.toObject(clase) }.orEmpty())
            alCambiar()
        }
    }

    /** Libera las suscripciones para evitar actualizaciones fuera de la pantalla activa. */
    fun detener() { suscripciones.forEach { it.remove() }; suscripciones.clear() }

    /** Genera un identificador aleatorio para un registro o archivo. */
    fun nuevoId(): String = UUID.randomUUID().toString()

    /** Guarda el perfil privado sin almacenar contraseñas. */
    fun guardarPerfil(perfil: Estudiante) {
        if (demostracion) { estudiante = perfil; alCambiar(); return }
        documentoEstudiante.set(perfil).addOnFailureListener(alFallar)
    }

    /** Guarda un registro académico y permite que Firestore sincronice los cambios pendientes. */
    fun guardar(coleccion: String, id: String, valor: Any) {
        if (demostracion) {
            when (valor) {
                is Materia -> materias = materias.filterNot { it.id == id } + valor
                is Horario -> horarios = horarios.filterNot { it.id == id } + valor
                is Actividad -> actividades = actividades.filterNot { it.id == id } + valor
                is Apunte -> apuntes = apuntes.filterNot { it.id == id } + valor
            }
            alCambiar(); return
        }
        val datos = if (valor is Apunte) valor.copy(adjuntos = emptyList()) else valor
        documentoEstudiante.collection(coleccion).document(id).set(datos).addOnFailureListener(alFallar)
    }

    /** Elimina un registro individual de la colección privada del estudiante. */
    fun eliminar(coleccion: String, id: String) {
        if (demostracion) {
            when (coleccion) {
                "materias" -> materias = materias.filterNot { it.id == id }
                "horarios" -> horarios = horarios.filterNot { it.id == id }
                "actividades" -> actividades = actividades.filterNot { it.id == id }
                "apuntes" -> apuntes = apuntes.filterNot { it.id == id }
            }
            alCambiar(); return
        }
        documentoEstudiante.collection(coleccion).document(id).delete().addOnFailureListener(alFallar)
    }

    /** Exporta un adjunto local al destino elegido por el estudiante (se integra en el módulo de Edwin). */
    fun descargarAdjunto(adjunto: Adjunto, destino: Uri, alTerminar: () -> Unit) {
        alTerminar()
    }

    /** Elimina los archivos ficticios al finalizar la demostración (se integra en el módulo de Edwin). */
    fun limpiarDemostracion() { }

    /** Carga datos ficticios de ejemplo que nunca se envían a Firebase. */
    private fun cargarEjemplo() {
        estudiante = Estudiante("Estudiante de ejemplo", "ejemplo@alumno.udb.edu.sv", "EM260001", "Ingeniería en Ciencias de la Computación", "02 / 2026")
        materias = listOf(Materia("apr", "Administración de Proyectos", "APR404", "Docente de ejemplo", "docente@udb.edu.sv"), Materia("dsm", "Desarrollo de Aplicaciones Móviles", "DSM441", "Docente de ejemplo", "docente@udb.edu.sv"))
        horarios = listOf(Horario("h1", "apr", 1, "07:00", "09:00", "Aula 204"), Horario("h2", "dsm", 3, "09:00", "11:00", "Laboratorio 3"))
        actividades = listOf(Actividad("a1", "apr", "Entrega del proyecto de cátedra", "Revisar los requisitos y preparar la entrega.", "Proyecto", System.currentTimeMillis() + 172800000, "pendiente", null, 40.0), Actividad("a2", "apr", "Primer parcial", "", "Parcial", System.currentTimeMillis() - 86400000, "entregado", 8.5, 30.0), Actividad("a3", "dsm", "Laboratorio de Firebase", "", "Laboratorio", System.currentTimeMillis() + 259200000, "pendiente", null, 20.0))
        apuntes = listOf(Apunte("n1", "apr", "Planificación por sprints", "Un sprint organiza un conjunto de actividades con un objetivo y una duración definidos."))
        noticias = listOf(Noticia("ejemplo", "Tu organización académica, en un solo lugar", "Esta tarjeta es un ejemplo de demostración. Las noticias reales las publica un administrador en Firebase.", System.currentTimeMillis(), "", "Ejemplo"))
    }
}
