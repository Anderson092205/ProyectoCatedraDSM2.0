package com.example.udb_academic.datos

import com.example.udb_academic.modelo.Estudiante
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

object Autenticacion {
    private val acceso get() = FirebaseAuth.getInstance().apply { setLanguageCode("es") }

    /** Valida que el correo pertenezca al dominio institucional estudiantil o docente. */
    fun esCorreoInstitucional(correo: String): Boolean = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@(?:alumno\\.)?udb\\.edu\\.sv$", RegexOption.IGNORE_CASE).matches(correo.trim())

    /** Registra una cuenta institucional y envía el correo de verificación. */
    fun registrar(nombre: String, correo: String, contrasena: String, resultado: (String?, Exception?) -> Unit) {
        if (!esCorreoInstitucional(correo)) { resultado(null, IllegalArgumentException("Usá tu correo @alumno.udb.edu.sv o @udb.edu.sv.")); return }
        acceso.createUserWithEmailAndPassword(correo.trim().lowercase(), contrasena).addOnSuccessListener { credencial ->
            val usuario = credencial.user ?: return@addOnSuccessListener
            usuario.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(nombre.trim()).build()).continueWithTask { anterior ->
                anterior.result
                usuario.sendEmailVerification()
            }.addOnSuccessListener { resultado("Revisá tu correo institucional y verificá la cuenta antes de ingresar.", null) }.addOnFailureListener { resultado(null, it) }
        }.addOnFailureListener { resultado(null, it) }
    }

    /** Inicia sesión y exige la verificación del correo institucional. */
    fun ingresar(correo: String, contrasena: String, resultado: (Exception?) -> Unit) {
        if (!esCorreoInstitucional(correo)) { resultado(IllegalArgumentException("Usá tu correo @alumno.udb.edu.sv o @udb.edu.sv.")); return }
        acceso.signInWithEmailAndPassword(correo.trim().lowercase(), contrasena).addOnSuccessListener { credencial ->
            if (credencial.user?.isEmailVerified != true) resultado(IllegalStateException("Verificá tu correo para activar la cuenta. Podés reenviar el enlace desde esta pantalla."))
            else resultado(null)
        }.addOnFailureListener(resultado)
    }

    /** Envía un enlace de recuperación al correo institucional indicado. */
    fun recuperar(correo: String, resultado: (Exception?) -> Unit) {
        if (!esCorreoInstitucional(correo)) { resultado(IllegalArgumentException("Escribí tu correo @alumno.udb.edu.sv o @udb.edu.sv.")); return }
        acceso.sendPasswordResetEmail(correo.trim().lowercase()).addOnSuccessListener { resultado(null) }.addOnFailureListener(resultado)
    }

    /** Reenvía la verificación a una cuenta autenticada que aún no esté activada. */
    fun reenviarVerificacion(resultado: (Exception?) -> Unit) {
        val usuario = acceso.currentUser
        if (usuario == null) { resultado(IllegalStateException("Ingresá primero con tu correo y contraseña para reenviar el enlace.")); return }
        usuario.sendEmailVerification().addOnSuccessListener { resultado(null) }.addOnFailureListener(resultado)
    }

    /** Reautentica al estudiante y actualiza su contraseña sin guardarla en la base de datos. */
    fun cambiarContrasena(actual: String, nueva: String, resultado: (Exception?) -> Unit) {
        val usuario = acceso.currentUser ?: return
        usuario.reauthenticate(EmailAuthProvider.getCredential(usuario.email.orEmpty(), actual)).continueWithTask { tarea ->
            tarea.result
            usuario.updatePassword(nueva)
        }.addOnSuccessListener { resultado(null) }.addOnFailureListener(resultado)
    }

    /** Crea el perfil inicial solamente cuando todavía no existe. */
    fun asegurarPerfil(resultado: (Exception?) -> Unit) {
        val usuario = acceso.currentUser ?: return
        val referencia = FirebaseFirestore.getInstance().collection("estudiantes").document(usuario.uid)
        referencia.get().addOnSuccessListener { documento ->
            if (documento.exists()) resultado(null)
            else referencia.set(Estudiante(usuario.displayName.orEmpty(), usuario.email.orEmpty())).addOnSuccessListener { resultado(null) }.addOnFailureListener(resultado)
        }.addOnFailureListener(resultado)
    }
}
