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

    /** Formatea una nota con dos decimales para presentarla en pantalla. */
    fun notaTexto(nota: Double): String = String.format(Locale.forLanguageTag("es-SV"), "%.2f", nota)
}
