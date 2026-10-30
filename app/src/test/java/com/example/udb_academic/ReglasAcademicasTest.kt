package com.example.udb_academic

import com.example.udb_academic.dominio.ReglasAcademicas
import org.junit.Assert.*
import org.junit.Test

class ReglasAcademicasTest {
    /** Comprueba que solamente se admita el dominio institucional exacto. */
    @Test fun validarDominioInstitucional() {
        assertTrue(ReglasAcademicas.esCorreoInstitucional("estudiante@alumno.udb.edu.sv")); assertTrue(ReglasAcademicas.esCorreoInstitucional("docente@udb.edu.sv"))
        assertTrue(ReglasAcademicas.esCorreoInstitucional(" Estudiante@ALUMNO.UDB.EDU.SV "))
        listOf("estudiante@otro.udb.edu.sv", "estudiante@gmail.com", "estudiante@alumno.udb.edu.sv.ejemplo.com", "@alumno.udb.edu.sv", "@udb.edu.sv", "a b@alumno.udb.edu.sv").forEach { assertFalse(ReglasAcademicas.esCorreoInstitucional(it)) }
    }
}
