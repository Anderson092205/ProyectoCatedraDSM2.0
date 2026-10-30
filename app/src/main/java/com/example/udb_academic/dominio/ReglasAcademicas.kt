package com.example.udb_academic.dominio

object ReglasAcademicas {
    /** Valida que el correo pertenezca exclusivamente al dominio institucional. */
    fun esCorreoInstitucional(correo: String): Boolean = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:alumno\\.)?udb\\.edu\\.sv$", RegexOption.IGNORE_CASE).matches(correo.trim())
}
