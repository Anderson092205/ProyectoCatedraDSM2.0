package com.example.udb_academic

import com.example.udb_academic.dominio.ReglasAcademicas
import com.example.udb_academic.modelo.Actividad
import com.example.udb_academic.modelo.Horario
import org.junit.Assert.*
import org.junit.Test

class ReglasAcademicasTest {
    /** Comprueba que solamente se admita el dominio institucional exacto. */
    @Test fun validarDominioInstitucional() {
        assertTrue(ReglasAcademicas.esCorreoInstitucional("estudiante@alumno.udb.edu.sv")); assertTrue(ReglasAcademicas.esCorreoInstitucional("docente@udb.edu.sv"))
        assertTrue(ReglasAcademicas.esCorreoInstitucional(" Estudiante@ALUMNO.UDB.EDU.SV "))
        listOf("estudiante@otro.udb.edu.sv", "estudiante@gmail.com", "estudiante@alumno.udb.edu.sv.ejemplo.com", "@alumno.udb.edu.sv", "@udb.edu.sv", "a b@alumno.udb.edu.sv").forEach { assertFalse(ReglasAcademicas.esCorreoInstitucional(it)) }
    }
    /** Verifica el cálculo ponderado sin normalizar notas pendientes. */
    @Test fun calcularAcumuladoPonderado() {
        val actividades = listOf(Actividad(id = "uno", materiaId = "apr", nota = 10.0, porcentaje = 30.0), Actividad(id = "dos", materiaId = "apr", nota = 6.0, porcentaje = 70.0))
        assertEquals(7.2, ReglasAcademicas.calcularPromedio(actividades), 0.000001)
        assertEquals(3.0, ReglasAcademicas.calcularPromedio(listOf(actividades[0], actividades[1].copy(nota = null))), 0.000001)
        assertEquals(0.0, ReglasAcademicas.calcularPromedio(emptyList()), 0.0)
    }
    /** Comprueba que la media global use las materias como unidades independientes. */
    @Test fun calcularPromedioGlobal() {
        val actividades = listOf(Actividad(materiaId = "apr", nota = 8.0, porcentaje = 100.0), Actividad(materiaId = "dsm", nota = 10.0, porcentaje = 50.0), Actividad(materiaId = "dsm", nota = null, porcentaje = 50.0))
        assertEquals(6.5, ReglasAcademicas.calcularPromedioGlobal(actividades), 0.000001)
    }
    /** Impide porcentajes superiores al total y excluye la actividad que se está editando. */
    @Test fun validarPonderacionYNotas() {
        val anterior = Actividad(id = "uno", materiaId = "apr", porcentaje = 80.0)
        assertNotNull(ReglasAcademicas.validarEvaluacion(Actividad(id = "dos", materiaId = "apr", porcentaje = 30.0), listOf(anterior)))
        assertNull(ReglasAcademicas.validarEvaluacion(anterior.copy(porcentaje = 100.0), listOf(anterior)))
        assertNull(ReglasAcademicas.validarEvaluacion(Actividad(materiaId = "otra", porcentaje = 100.0), listOf(anterior)))
        listOf(-1.0, 11.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { assertNotNull(ReglasAcademicas.validarEvaluacion(anterior.copy(nota = it), emptyList())) }
        assertNotNull(ReglasAcademicas.validarEvaluacion(anterior.copy(porcentaje = Double.NaN), emptyList()))
    }
    /** Valida horas, cruces, clases consecutivas y días diferentes. */
    @Test fun validarConflictosDeHorario() {
        val anterior = Horario(id = "uno", dia = 1, horaInicio = "07:00", horaFin = "09:00")
        assertNotNull(ReglasAcademicas.validarHorario(Horario(id = "dos", dia = 1, horaInicio = "08:00", horaFin = "10:00"), listOf(anterior)))
        assertNull(ReglasAcademicas.validarHorario(Horario(id = "dos", dia = 1, horaInicio = "09:00", horaFin = "10:00"), listOf(anterior)))
        assertNull(ReglasAcademicas.validarHorario(anterior.copy(dia = 2, id = "dos"), listOf(anterior)))
        assertNull(ReglasAcademicas.validarHorario(anterior, listOf(anterior)))
        assertNotNull(ReglasAcademicas.validarHorario(anterior.copy(horaInicio = "25:00"), emptyList()))
        assertNotNull(ReglasAcademicas.validarHorario(anterior.copy(horaFin = "06:00"), emptyList()))
        assertNotNull(ReglasAcademicas.validarHorario(anterior.copy(dia = 6), emptyList()))
    }
}
