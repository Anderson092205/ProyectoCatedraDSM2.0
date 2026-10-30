package com.example.udb_academic.dominio

import com.example.udb_academic.modelo.Actividad
import java.util.Locale

object ReglasAcademicas {
    /** Valida que el correo pertenezca exclusivamente al dominio institucional. */
    fun esCorreoInstitucional(correo: String): Boolean = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:alumno\\.)?udb\\.edu\\.sv$", RegexOption.IGNORE_CASE).matches(correo.trim())

    /** Calcula la nota acumulada sobre diez sin redistribuir porcentajes pendientes. */
    fun calcularPromedio(actividades: List<Actividad>): Double = actividades.sumOf { (it.nota ?: 0.0) * it.porcentaje / 100.0 }

    /** Calcula la media de las materias que tienen evaluaciones configuradas. */
    fun calcularPromedioGlobal(actividades: List<Actividad>): Double = actividades.groupBy { it.materiaId }.values.map { calcularPromedio(it) }.let { if (it.isEmpty()) 0.0 else it.average() }

    /** Comprueba las notas y el porcentaje total de una materia antes de guardar. */
    fun validarEvaluacion(actividad: Actividad, existentes: List<Actividad>): String? {
        if (actividad.nota != null && (!actividad.nota.isFinite() || actividad.nota !in 0.0..10.0)) return "La nota debe estar entre 0 y 10."
        if (!actividad.porcentaje.isFinite() || actividad.porcentaje !in 0.0..100.0) return "El porcentaje debe estar entre 0 y 100."
        val total = existentes.filter { it.materiaId == actividad.materiaId && it.id != actividad.id }.sumOf { it.porcentaje } + actividad.porcentaje
        return if (total > 100.000001) "Los porcentajes de esta materia superarían el 100 %." else null
    }

    /** Formatea una nota con dos decimales para presentarla en pantalla. */
    fun notaTexto(nota: Double): String = String.format(Locale.forLanguageTag("es-SV"), "%.2f", nota)
}
