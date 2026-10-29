package com.example.udb_academic

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.CalendarContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.udb_academic.datos.Autenticacion
import com.example.udb_academic.datos.RepositorioAcademico
import com.example.udb_academic.dominio.ReglasAcademicas
import com.example.udb_academic.modelo.*
import com.example.udb_academic.notificaciones.Recordatorios
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ActividadPrincipal : AppCompatActivity() {
    private val azul = Color.rgb(23, 39, 75)
    private val gris = Color.rgb(89, 101, 120)
    private val fondo = Color.rgb(245, 246, 250)
    private val dorado = Color.rgb(168, 122, 25)
    private var repositorio: RepositorioAcademico? = null
    private var pantalla = "inicio"
    private var filtroActividades = "todas"
    private var filtroApuntes = ""
    private var diaSeleccionado = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1).coerceAtMost(5)
    private lateinit var raiz: LinearLayout
    private lateinit var contenido: LinearLayout
    private var apunteParaAdjuntar = ""
    private var adjuntoParaDescargar: Adjunto? = null
    private val formatoFecha = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.forLanguageTag("es-SV"))
    private var probarAlDarPermiso = false
    private val permisoNotificaciones = registerForActivityResult(ActivityResultContracts.RequestPermission()) { permitido ->
        if (probarAlDarPermiso) {
            if (permitido) Recordatorios.probar(this) else aviso("Permití las notificaciones en los ajustes de la aplicación para ver los recordatorios.", true)
            probarAlDarPermiso = false
        }
    }
    private val elegirArchivo = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) procesarAdjunto(uri) }
    private val guardarArchivo = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val adjunto = adjuntoParaDescargar
        if (uri != null && adjunto != null) repositorio?.descargarAdjunto(adjunto, uri) { aviso("Archivo descargado.") }
    }


    /** Restaura la navegación y presenta el acceso o la sesión verificada. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        probarAlDarPermiso = savedInstanceState?.getBoolean("probarAlDarPermiso") ?: false
        pantalla = savedInstanceState?.getString("pantalla") ?: "inicio"
        apunteParaAdjuntar = savedInstanceState?.getString("apunteParaAdjuntar").orEmpty()
        filtroActividades = savedInstanceState?.getString("filtroActividades") ?: "todas"
        filtroApuntes = savedInstanceState?.getString("filtroApuntes").orEmpty()
        diaSeleccionado = savedInstanceState?.getInt("diaSeleccionado", diaSeleccionado) ?: diaSeleccionado
        if (savedInstanceState?.containsKey("adjuntoRuta") == true) adjuntoParaDescargar = Adjunto(savedInstanceState.getString("adjuntoNombre").orEmpty(), savedInstanceState.getString("adjuntoRuta").orEmpty(), savedInstanceState.getString("adjuntoTipo").orEmpty(), savedInstanceState.getLong("adjuntoTamano"))
        if (savedInstanceState?.getBoolean("demostracion") == true) iniciarSesion(true)
        else if (BuildConfig.FIREBASE_CONFIGURADO && FirebaseAuth.getInstance().currentUser?.let { it.isEmailVerified && ReglasAcademicas.esCorreoInstitucional(it.email.orEmpty()) } == true) iniciarSesion(false)
        else mostrarAcceso()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /** Regresa al inicio antes de salir de la aplicación. */
            override fun handleOnBackPressed() {
                if (repositorio != null && pantalla != "inicio") navegar("inicio")
                else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
            }
        })
    }

    /** Reactiva las consultas cuando la aplicación vuelve al primer plano. */
    override fun onStart() { super.onStart(); repositorio?.escuchar() }

    /** Detiene las consultas visibles cuando la aplicación pasa al fondo. */
    override fun onStop() { repositorio?.detener(); super.onStop() }

    /** Conserva la pantalla y el apunte seleccionado durante una recreación. */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("probarAlDarPermiso", probarAlDarPermiso)
        outState.putString("pantalla", pantalla)
        outState.putString("apunteParaAdjuntar", apunteParaAdjuntar)
        outState.putBoolean("demostracion", repositorio?.demostracion == true)
        outState.putString("filtroActividades", filtroActividades)
        outState.putString("filtroApuntes", filtroApuntes)
        outState.putInt("diaSeleccionado", diaSeleccionado)
        adjuntoParaDescargar?.let { adjunto ->
            outState.putString("adjuntoNombre", adjunto.nombre)
            outState.putString("adjuntoRuta", adjunto.ruta)
            outState.putString("adjuntoTipo", adjunto.tipo)
            outState.putLong("adjuntoTamano", adjunto.tamano)
        }
        super.onSaveInstanceState(outState)
    }

    /** Crea el contenedor principal y respeta las barras del sistema. */
    private fun prepararRaiz() {
        raiz = columna().apply { setBackgroundColor(fondo) }
        setContentView(raiz)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { vista, margenes ->
            val barras = margenes.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            vista.setPadding(barras.left, barras.top, barras.right, barras.bottom)
            margenes
        }
    }

    /** Construye una columna con dimensiones adaptables y separación opcional. */
    private fun columna(espacio: Int = 0): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(-1, -2)
        setPadding(dp(espacio), dp(espacio), dp(espacio), dp(espacio))
    }

    /** Convierte unidades independientes de densidad en píxeles. */
    private fun dp(valor: Int): Int = (valor * resources.displayMetrics.density).toInt()

    /** Agrega texto legible con el nivel de énfasis indicado. */
    private fun texto(destino: LinearLayout, valor: String, tamano: Float = 14f, color: Int = gris, negrita: Boolean = false): TextView = TextView(this).apply {
        text = valor; textSize = tamano; setTextColor(color)
        if (negrita) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(7)); setLineSpacing(dp(2).toFloat(), 1f)
        destino.addView(this, LinearLayout.LayoutParams(-1, -2))
    }

    /** Agrega una acción con un área táctil accesible. */
    private fun boton(destino: LinearLayout, titulo: String, secundario: Boolean = false, accion: () -> Unit): MaterialButton = MaterialButton(this, null, if (secundario) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
        text = titulo; isAllCaps = false; minHeight = dp(48); cornerRadius = dp(14)
        setOnClickListener { accion() }
        destino.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(4) })
    }

    /** Crea una tarjeta con márgenes y esquinas redondeadas. */
    private fun tarjeta(destino: LinearLayout = contenido, oscura: Boolean = false, construir: (LinearLayout) -> Unit): MaterialCardView {
        val tarjeta = MaterialCardView(this).apply {
            radius = dp(18).toFloat(); cardElevation = 0f; strokeWidth = dp(1)
            setStrokeColor(if (oscura) azul else Color.rgb(229, 233, 241))
            setCardBackgroundColor(if (oscura) azul else Color.WHITE)
        }
        val interior = columna(18)
        tarjeta.addView(interior)
        destino.addView(tarjeta, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        construir(interior)
        return tarjeta
    }

    /** Agrega una entrada con etiqueta y el tipo de teclado apropiado. */
    private fun entrada(destino: LinearLayout, etiqueta: String, valor: String = "", tipo: Int = InputType.TYPE_CLASS_TEXT, multilinea: Boolean = false): TextInputEditText {
        val marco = TextInputLayout(this).apply { hint = etiqueta; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE; setBoxCornerRadii(dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat(), dp(12).toFloat()) }
        val campo = TextInputEditText(marco.context).apply { inputType = tipo; setText(valor); textSize = 15f; setSingleLine(!multilinea); if (multilinea) { minLines = 4; gravity = Gravity.TOP; inputType = tipo or InputType.TYPE_TEXT_FLAG_MULTI_LINE } }
        if (tipo == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)) marco.endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        marco.addView(campo, LinearLayout.LayoutParams(-1, -2))
        destino.addView(marco, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(7) })
        return campo
    }

    /** Presenta opciones identificadas por texto dentro de un formulario. */
    private fun selector(destino: LinearLayout, etiqueta: String, opciones: List<String>, seleccionado: Int = 0): Spinner {
        texto(destino, etiqueta, 13f, azul, true)
        return Spinner(this).apply {
            adapter = ArrayAdapter(this@ActividadPrincipal, android.R.layout.simple_spinner_dropdown_item, opciones)
            setSelection(seleccionado.coerceAtLeast(0)); minimumHeight = dp(48); contentDescription = etiqueta
            destino.addView(this, LinearLayout.LayoutParams(-1, dp(52)))
        }
    }

    /** Presenta el acceso institucional y las acciones de registro y recuperación. */
    private fun mostrarAcceso(registro: Boolean = false) {
        prepararRaiz()
        val desplazamiento = ScrollView(this).apply { isFillViewport = true }
        val panel = columna(24)
        desplazamiento.addView(panel); raiz.addView(desplazamiento, LinearLayout.LayoutParams(-1, -1))
        texto(panel, "U", 42f, dorado, true).gravity = Gravity.CENTER
        texto(panel, "UDB Academic", 28f, azul, true).gravity = Gravity.CENTER
        texto(panel, "Tu vida académica, organizada.", 15f).gravity = Gravity.CENTER
        tarjeta(panel) { formulario ->
            texto(formulario, if (registro) "Creá tu cuenta" else "Te damos la bienvenida", 22f, azul, true)
            texto(formulario, "Acceso exclusivo con correo @alumno.udb.edu.sv o @udb.edu.sv")
            if (!BuildConfig.FIREBASE_CONFIGURADO) texto(formulario, "La conexión con Firebase está pendiente. Podés recorrer las pantallas con datos de ejemplo.", 14f, dorado)
            val nombre = if (registro) entrada(formulario, "Nombre completo") else null
            val correo = entrada(formulario, "Correo institucional", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val contrasena = entrada(formulario, "Contraseña", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)

            val confirmacion = if (registro) entrada(formulario, "Confirmar contraseña", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD) else null
            val error = texto(formulario, "", 14f, Color.rgb(169, 35, 45))
            lateinit var acceder: MaterialButton
            acceder = boton(formulario, if (registro) "Crear cuenta" else "Iniciar sesión") {
                val direccion = correo.text.toString().trim()
                val clave = contrasena.text.toString()
                when {
                    !BuildConfig.FIREBASE_CONFIGURADO -> error.text = "Primero hay que vincular el proyecto con Firebase."
                    !ReglasAcademicas.esCorreoInstitucional(direccion) -> { correo.error = "Usá tu correo @alumno.udb.edu.sv o @udb.edu.sv"; correo.requestFocus() }
                    registro && nombre?.text.toString().trim().length !in 2..100 -> nombre?.error = "Ingresá entre 2 y 100 caracteres"
                    clave.length < 8 -> contrasena.error = "Usá al menos 8 caracteres"
                    registro && clave != confirmacion?.text.toString() -> confirmacion?.error = "Las contraseñas no coinciden"
                    else -> {
                        acceder.isEnabled = false; error.text = "Conectando…"
                        if (registro) Autenticacion.registrar(nombre?.text.toString(), direccion, clave) { mensaje, fallo ->
                            acceder.isEnabled = true
                            if (fallo != null) error.text = mensajeError(fallo)
                            else { mostrarAcceso(); aviso(mensaje.orEmpty(), true) }
                        } else Autenticacion.ingresar(direccion, clave) { fallo ->
                            acceder.isEnabled = true
                            if (fallo != null) error.text = mensajeError(fallo)
                            else iniciarSesion(false)
                        }
                    }
                }
            }
            boton(formulario, if (registro) "Ya tengo cuenta" else "Crear una cuenta", true) { mostrarAcceso(!registro) }
            if (!registro) {
                boton(formulario, "¿Olvidaste tu contraseña?", true) {
                    if (BuildConfig.FIREBASE_CONFIGURADO) Autenticacion.recuperar(correo.text.toString()) { fallo -> error.text = if (fallo == null) "Si la cuenta existe, recibirás un enlace de recuperación." else mensajeError(fallo) }
                }
                boton(formulario, "Reenviar verificación", true) {
                    if (BuildConfig.FIREBASE_CONFIGURADO) Autenticacion.reenviarVerificacion { fallo -> error.text = if (fallo == null) "Revisá tu correo institucional." else mensajeError(fallo) }
                }
            }
        }
        boton(panel, "Ver demostración con datos de ejemplo", true) { pantalla = "inicio"; iniciarSesion(true) }
        texto(panel, "UDB Academic · Organización estudiantil", 12f).gravity = Gravity.CENTER
    }

    /** Abre una sesión de trabajo y conecta las consultas con las pantallas. */
    private fun iniciarSesion(demostracion: Boolean) {
        repositorio?.detener()
        repositorio = RepositorioAcademico(applicationContext, demostracion).apply {
            alCambiar = {
                mostrarAplicacion()
                if (!demostracion) Recordatorios.programar(this@ActividadPrincipal, actividades, FirebaseAuth.getInstance().currentUser?.uid.orEmpty())
            }
            alFallar = { fallo -> aviso(mensajeError(fallo), true) }
        }
        mostrarAplicacion()
        if (!demostracion) {
            Autenticacion.asegurarPerfil { fallo -> if (fallo != null) aviso(mensajeError(fallo), true) }
            Recordatorios.crearCanal(this)
            if (Build.VERSION.SDK_INT >= 33) permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        repositorio?.escuchar()
    }

    /** Actualiza la ruta seleccionada y vuelve a dibujar su contenido. */
    private fun navegar(destino: String) { pantalla = destino; mostrarAplicacion() }

    /** Compone la cabecera, la pantalla activa y la navegación inferior. */
    private fun mostrarAplicacion() {
        val datos = repositorio ?: return
        prepararRaiz()
        val cabecera = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(6), dp(14), dp(6)); setBackgroundColor(Color.WHITE) }
        val marca = columna()
        texto(marca, "UDB Academic", 20f, azul, true)
        texto(marca, if (datos.demostracion) "DEMOSTRACIÓN · Datos ficticios" else if (datos.escriturasPendientes) "Cambios pendientes de sincronizar" else if (datos.desdeCache) "Datos guardados en el dispositivo" else "Tu espacio académico", 11f)
        cabecera.addView(marca, LinearLayout.LayoutParams(0, -2, 1f))
        boton(cabecera, "Noticias", true) { navegar("noticias") }.layoutParams = LinearLayout.LayoutParams(-2, dp(52))
        raiz.addView(cabecera)
        val desplazamiento = ScrollView(this).apply { isFillViewport = true }
        contenido = columna(20)
        desplazamiento.addView(contenido)
        raiz.addView(desplazamiento, LinearLayout.LayoutParams(-1, 0, 1f))
        when {
            pantalla == "horario" -> mostrarHorario(datos)
            pantalla == "actividades" -> mostrarActividades(datos)
            pantalla == "apuntes" -> mostrarApuntes(datos)
            pantalla == "perfil" -> mostrarPerfil(datos)
            pantalla == "noticias" -> mostrarNoticias(datos)
            pantalla == "calculadora" -> mostrarCalculadora(datos)
            pantalla.startsWith("materia:") -> mostrarMateria(datos, pantalla.substringAfter(":"))
            else -> mostrarInicio(datos)
        }
        val navegacion = BottomNavigationView(this).apply {
            setBackgroundColor(Color.WHITE); labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
            itemIconTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(azul, gris))
            itemTextColor = itemIconTintList
            val opciones = listOf("Inicio", "Horario", "Actividades", "Apuntes", "Perfil")
            val iconos = listOf(R.drawable.ic_inicio, R.drawable.ic_horario, R.drawable.ic_actividades, R.drawable.ic_apuntes, R.drawable.ic_perfil)
            opciones.forEachIndexed { indice, titulo -> menu.add(0, indice + 1, indice, titulo).setIcon(iconos[indice]) }
            val rutas = listOf("inicio", "horario", "actividades", "apuntes", "perfil")
            selectedItemId = (rutas.indexOf(pantalla).takeIf { it >= 0 } ?: if (pantalla == "calculadora") 2 else 0) + 1
            setOnItemSelectedListener { opcion -> navegar(rutas[opcion.itemId - 1]); true }
        }
        raiz.addView(navegacion, LinearLayout.LayoutParams(-1, -2))
    }

    /** Muestra los datos de la cuenta y sus acciones de edición y cierre. */
    private fun mostrarPerfil(datos: RepositorioAcademico) {
        texto(contenido, "Mi perfil", 27f, azul, true)
        tarjeta { panel ->
            texto(panel, datos.estudiante.nombre.ifBlank { "Estudiante" }, 24f, azul, true)
            texto(panel, datos.estudiante.correo)
            texto(panel, "Carnet: ${datos.estudiante.carnet.ifBlank { "Sin registrar" }}")
            texto(panel, "Carrera: ${datos.estudiante.carrera.ifBlank { "Sin registrar" }}")
            texto(panel, "Ciclo: ${datos.estudiante.ciclo.ifBlank { "Sin registrar" }}")
            boton(panel, "Editar perfil", true) { formularioPerfil() }
            boton(panel, "Cambiar contraseña", true) { if (datos.demostracion) aviso("La demostración no tiene credenciales.") else formularioContrasena() }
            boton(panel, if (datos.demostracion) "Salir de la demostración" else "Cerrar sesión", true) { cerrarSesion() }
        }
        tarjeta { panel ->
            texto(panel, "Recordatorios locales", 20f, azul, true)
            texto(panel, "El teléfono muestra avisos antes de tus entregas. Podés probar una notificación ahora; no se usa un servidor ni un plan de pago.")
            boton(panel, "Probar recordatorio ahora", true) { probarRecordatorio() }
        }
        tarjeta(oscura = true) { panel ->
            texto(panel, "Tu información es privada", 18f, Color.WHITE, true)
            texto(panel, if (datos.demostracion) "Estás usando datos ficticios. Los cambios de la demostración se pierden al salir." else "Tus materias, actividades, apuntes y archivos están asociados a tu cuenta. Los cambios académicos se guardan en el dispositivo y se sincronizan al recuperar conexión.", 14f, Color.WHITE)
        }
    }

    /** Crea un formulario desplazable que valida los datos antes de cerrarse. */
    private fun dialogo(titulo: String, construir: (LinearLayout, TextView) -> (() -> Boolean)) {
        val panel = columna(20)
        val error = TextView(this).apply { setTextColor(Color.rgb(169, 35, 45)); textSize = 14f }
        val guardar = construir(panel, error)
        panel.addView(error)
        val desplazamiento = ScrollView(this).apply { addView(panel) }
        val ventana = MaterialAlertDialogBuilder(this).setTitle(titulo).setView(desplazamiento).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
        ventana.setOnShowListener { ventana.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { if (guardar()) ventana.dismiss() } }
        ventana.show()
    }

    /** Edita los datos académicos del estudiante manteniendo su correo de acceso. */
    private fun formularioPerfil() {
        val datos = repositorio ?: return
        dialogo("Editar perfil") { panel, error ->
            val nombre = entrada(panel, "Nombre completo", datos.estudiante.nombre)
            val carnet = entrada(panel, "Carnet", datos.estudiante.carnet)
            val carrera = entrada(panel, "Carrera", datos.estudiante.carrera)
            val ciclo = entrada(panel, "Ciclo", datos.estudiante.ciclo)
            val guardar: () -> Boolean = {
                if (nombre.text.toString().trim().length !in 2..100 || carnet.text.toString().length > 30 || carrera.text.toString().length > 150 || ciclo.text.toString().length > 30) { error.text = "Revisá los campos: nombre de 2 a 100, carnet y ciclo hasta 30, carrera hasta 150 caracteres."; false }
                else { datos.guardarPerfil(Estudiante(nombre.text.toString().trim(), datos.estudiante.correo, carnet.text.toString().trim(), carrera.text.toString().trim(), ciclo.text.toString().trim())); true }
            }
            guardar
        }
    }

    /** Solicita la contraseña actual y una nueva contraseña confirmada. */
    private fun formularioContrasena() {
        dialogo("Cambiar contraseña") { panel, error ->
            val actual = entrada(panel, "Contraseña actual", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val nueva = entrada(panel, "Nueva contraseña", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val confirmar = entrada(panel, "Confirmar nueva contraseña", tipo = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            val guardar: () -> Boolean = {
                if (nueva.text.toString().length < 8 || nueva.text.toString() != confirmar.text.toString()) { error.text = "Usá al menos 8 caracteres y confirmá la misma contraseña."; false }
                else { Autenticacion.cambiarContrasena(actual.text.toString(), nueva.text.toString()) { fallo -> if (fallo == null) aviso("Contraseña actualizada.") else aviso(mensajeError(fallo), true) }; true }
            }
            guardar
        }
    }

    /** Cierra la sesión y cancela los recordatorios de la cuenta en el teléfono. */
    private fun cerrarSesion() {
        val datos = repositorio ?: return
        val terminar = {
            Recordatorios.cancelar(this)
            datos.detener(); repositorio = null; pantalla = "inicio"
            if (!datos.demostracion) FirebaseAuth.getInstance().signOut()
            mostrarAcceso()
        }
        if (datos.demostracion) datos.limpiarDemostracion()
        terminar()
    }

    /** Explica los estados vacíos de cada sección. */
    private fun vacio(titulo: String, detalle: String) { tarjeta { panel -> texto(panel, titulo, 17f, azul, true); texto(panel, detalle) } }

    /** Solicita confirmación antes de eliminar datos de la aplicación. */
    private fun confirmar(titulo: String, mensaje: String, accion: () -> Unit) { MaterialAlertDialogBuilder(this).setTitle(titulo).setMessage(mensaje).setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ -> accion() }.show() }

    /** Muestra mensajes breves al estudiante. */
    private fun aviso(mensaje: String, largo: Boolean = false) { Toast.makeText(this, mensaje, if (largo) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show() }

    /** Traduce errores frecuentes de Firebase a mensajes comprensibles. */
    private fun mensajeError(error: Exception): String = when {
        error is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND" -> "Revisá tu correo y contraseña."
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Este correo ya tiene una cuenta. Iniciá sesión o recuperá tu contraseña."
            "ERROR_WEAK_PASSWORD" -> "Elegí una contraseña más segura."
            "ERROR_TOO_MANY_REQUESTS" -> "Hay varios intentos seguidos. Esperá un momento antes de intentar de nuevo."
            "ERROR_OPERATION_NOT_ALLOWED" -> "El acceso por correo y contraseña debe habilitarse en Firebase."
            else -> "No se pudo completar el acceso. Revisá la conexión e intentá de nuevo."
        }
        error is FirebaseFirestoreException && error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Firebase rechazó la operación. Revisá la verificación de tu correo y las reglas de seguridad."
        error is com.google.firebase.FirebaseNetworkException -> "No hay conexión. Para iniciar sesión o subir archivos necesitás Internet."
        error is java.io.IOException -> "No se pudo leer o guardar el archivo. Revisá el espacio disponible y el archivo seleccionado."
        error is IllegalArgumentException || error is IllegalStateException -> error.message ?: "Revisá los datos ingresados."
        else -> "No se pudo completar la operación. Revisá la conexión y la configuración de Firebase."
    }
}