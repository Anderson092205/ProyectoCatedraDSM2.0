package com.example.udb_academic.modelo

data class Estudiante(val nombre: String = "", val correo: String = "", val carnet: String = "", val carrera: String = "", val ciclo: String = "")
data class Materia(val id: String = "", val nombre: String = "", val codigo: String = "", val docente: String = "", val correoDocente: String = "")
data class Horario(val id: String = "", val materiaId: String = "", val dia: Int = 1, val horaInicio: String = "07:00", val horaFin: String = "08:00", val aula: String = "")
data class Actividad(val id: String = "", val materiaId: String = "", val titulo: String = "", val descripcion: String = "", val tipo: String = "Tarea", val fechaEntrega: Long = 0, val estado: String = "pendiente", val nota: Double? = null, val porcentaje: Double = 0.0)
data class Adjunto(val nombre: String = "", val ruta: String = "", val tipo: String = "", val tamano: Long = 0)
data class Apunte(val id: String = "", val materiaId: String = "", val titulo: String = "", val contenido: String = "", val adjuntos: List<Adjunto> = emptyList())
data class Noticia(val id: String = "", val titulo: String = "", val descripcion: String = "", val fecha: Long = 0, val enlace: String = "", val categoria: String = "Noticia")
