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

    /** Presenta el resumen del día, próximas entregas y últimos apuntes. */
    private fun mostrarInicio(datos: RepositorioAcademico) {
        texto(contenido, "¡Hola, ${datos.estudiante.nombre.substringBefore(" ").ifBlank { "estudiante" }}!", 28f, azul, true)
        texto(contenido, SimpleDateFormat("EEEE, d 'de' MMMM", Locale.forLanguageTag("es-SV")).format(Date()).replaceFirstChar { it.uppercase() })
        tarjeta(oscura = true) { panel ->
            texto(panel, "TU CICLO DE UN VISTAZO", 12f, Color.rgb(222, 193, 115), true)
            texto(panel, "${datos.materias.size} materias  ·  ${datos.actividades.count { it.estado == "pendiente" }} pendientes", 21f, Color.WHITE, true)
            texto(panel, "Promedio acumulado: ${ReglasAcademicas.notaTexto(ReglasAcademicas.calcularPromedioGlobal(datos.actividades))} / 10", 15f, Color.WHITE)
        }
        texto(contenido, "Clases de hoy", 20f, azul, true)
        val hoy = (Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val clases = datos.horarios.filter { it.dia == hoy }.sortedBy { it.horaInicio }
        if (clases.isEmpty()) vacio("Hoy no tenés clases registradas.", "Organizá tu semana desde Horario.")
        clases.forEach { horario -> tarjeta { panel ->
            texto(panel, nombreMateria(horario.materiaId), 16f, azul, true)
            texto(panel, "${horario.horaInicio}–${horario.horaFin} · ${horario.aula}")
            boton(panel, "Ver asignatura", true) { navegar("materia:${horario.materiaId}") }
        } }
        texto(contenido, "Próximas entregas", 20f, azul, true)
        val pendientes = datos.actividades.filter { it.estado == "pendiente" }.sortedBy { it.fechaEntrega }.take(3)
        if (pendientes.isEmpty()) vacio("Estás al día", "Agregá tus evaluaciones desde Actividades.")
        pendientes.forEach { tarjetaActividad(it) }
        texto(contenido, "Últimos apuntes", 20f, azul, true)
        if (datos.apuntes.isEmpty()) texto(contenido, "Tus notas de clase aparecerán aquí.")
        datos.apuntes.takeLast(2).reversed().forEach { apunte -> tarjeta { panel ->
            texto(panel, apunte.titulo, 16f, azul, true); texto(panel, nombreMateria(apunte.materiaId), 12f); texto(panel, apunte.contenido.take(150))
            boton(panel, "Abrir apuntes", true) { navegar("apuntes") }
        } }
    }

    /** Muestra el horario semanal y las acciones de gestión de materias y clases. */
    private fun mostrarHorario(datos: RepositorioAcademico) {
        texto(contenido, "Horario semanal", 27f, azul, true)
        texto(contenido, "Tu semana, organizada de lunes a viernes.")
        boton(contenido, "+ Agregar materia") { formularioMateria() }
        boton(contenido, "+ Agregar clase", true) { if (exigirMaterias()) formularioHorario() }
        val dias = selector(contenido, "Día de la semana", listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes"), diaSeleccionado - 1)
        dias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** Cambia el día visible cuando el estudiante selecciona otra opción. */
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (diaSeleccionado != position + 1) { diaSeleccionado = position + 1; mostrarAplicacion() } }
            /** Mantiene el día actual cuando no hay una selección nueva. */
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        val clases = datos.horarios.filter { it.dia == diaSeleccionado }.sortedBy { it.horaInicio }
        if (clases.isEmpty()) vacio("Sin clases este día", "Agregá una clase con su materia, aula y horario.")
        clases.forEach { horario -> tarjeta { panel ->
            texto(panel, "${horario.horaInicio} – ${horario.horaFin}", 14f, dorado, true)
            texto(panel, nombreMateria(horario.materiaId), 19f, azul, true); texto(panel, horario.aula)
            boton(panel, "Ver asignatura", true) { navegar("materia:${horario.materiaId}") }
            boton(panel, "Editar clase", true) { formularioHorario(horario) }
            boton(panel, "Agregar al calendario", true) { agregarAlCalendario(horario) }
            boton(panel, "Eliminar clase", true) { confirmar("Eliminar clase", "Se quitará esta clase del horario.") { datos.eliminar("horarios", horario.id) } }
        } }
        texto(contenido, "Mis materias", 20f, azul, true)
        if (datos.materias.isEmpty()) vacio("Comenzá por tus materias", "Registrá una materia para asociar clases, actividades y apuntes.")
        datos.materias.sortedBy { it.nombre }.forEach { materia -> tarjeta { panel ->
            texto(panel, materia.nombre, 17f, azul, true); texto(panel, materia.codigo)
            boton(panel, "Ver detalle", true) { navegar("materia:${materia.id}") }
        } }
    }

    /** Presenta el docente, su contacto y las actividades de una asignatura. */
    private fun mostrarMateria(datos: RepositorioAcademico, id: String) {
        val materia = datos.materias.find { it.id == id } ?: run { vacio("Materia no disponible", "Regresá a Horario para consultar las materias."); return }
        boton(contenido, "Volver al horario", true) { navegar("horario") }
        texto(contenido, materia.nombre, 27f, azul, true)
        tarjeta { panel ->
            texto(panel, materia.codigo, 13f, dorado, true)
            texto(panel, "Catedrático", 13f, gris, true); texto(panel, materia.docente.ifBlank { "Sin registrar" }, 18f, azul)
            texto(panel, materia.correoDocente.ifBlank { "Sin correo registrado" })
            if (materia.correoDocente.isNotBlank()) boton(panel, "Escribir al docente", true) { abrirEnlace("mailto:${Uri.encode(materia.correoDocente)}") }
            boton(panel, "Editar materia", true) { formularioMateria(materia) }
            boton(panel, "Eliminar materia", true) {
                if (datos.horarios.any { it.materiaId == id } || datos.actividades.any { it.materiaId == id } || datos.apuntes.any { it.materiaId == id }) aviso("Primero eliminá las clases, actividades y apuntes asociados a esta materia.", true)
                else confirmar("Eliminar materia", "Se quitará ${materia.nombre}.") { datos.eliminar("materias", id); navegar("horario") }
            }
        }
        texto(contenido, "Actividades de la materia", 20f, azul, true)
        val actividades = datos.actividades.filter { it.materiaId == id }.sortedBy { it.fechaEntrega }
        if (actividades.isEmpty()) texto(contenido, "Todavía no hay actividades registradas.")
        actividades.forEach { tarjetaActividad(it) }
    }

    /** Filtra las actividades por estado y las ordena por fecha de entrega. */
    private fun mostrarActividades(datos: RepositorioAcademico) {
        texto(contenido, "Actividades y evaluaciones", 26f, azul, true)
        texto(contenido, "Tareas, laboratorios, parciales y proyectos.")
        boton(contenido, "+ Nueva actividad") { if (exigirMaterias()) formularioActividad() }
        boton(contenido, "Calculadora de notas", true) { navegar("calculadora") }
        val opciones = listOf("Todas", "Pendientes", "Entregadas")
        val valores = listOf("todas", "pendiente", "entregado")
        val filtro = selector(contenido, "Mostrar actividades", opciones, valores.indexOf(filtroActividades))
        filtro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** Aplica el filtro de estado elegido por el estudiante. */
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (filtroActividades != valores[position]) { filtroActividades = valores[position]; mostrarAplicacion() } }
            /** Conserva el filtro actual si no se selecciona otra opción. */
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        val actividades = datos.actividades.filter { filtroActividades == "todas" || it.estado == filtroActividades }.sortedBy { it.fechaEntrega }
        if (actividades.isEmpty()) vacio("Sin actividades en esta vista", "Creá una actividad o cambiá el filtro.")
        actividades.forEach { tarjetaActividad(it) }
    }

    /** Presenta el estado, la fecha y las acciones de una actividad. */
    private fun tarjetaActividad(actividad: Actividad) {
        tarjeta { panel ->
            val vencida = actividad.estado == "pendiente" && actividad.fechaEntrega < System.currentTimeMillis()
            texto(panel, "${actividad.tipo.uppercase()} · ${if (actividad.estado == "entregado") "ENTREGADA" else if (vencida) "VENCIDA" else "PENDIENTE"}", 11f, if (vencida) Color.rgb(169, 35, 45) else dorado, true)
            texto(panel, actividad.titulo, 18f, azul, true); texto(panel, nombreMateria(actividad.materiaId), 13f)
            texto(panel, formatoFecha.format(Date(actividad.fechaEntrega)), 13f, azul)
            if (actividad.descripcion.isNotBlank()) texto(panel, actividad.descripcion)
            texto(panel, "Peso: ${ReglasAcademicas.notaTexto(actividad.porcentaje)} % · Nota: ${actividad.nota?.let { ReglasAcademicas.notaTexto(it) } ?: "Por registrar"}", 12f)
            boton(panel, if (actividad.estado == "entregado") "Marcar como pendiente" else "Marcar como entregada", true) { repositorio?.guardar("actividades", actividad.id, actividad.copy(estado = if (actividad.estado == "entregado") "pendiente" else "entregado")) }
            boton(panel, "Editar actividad y nota", true) { formularioActividad(actividad) }
            boton(panel, "Eliminar actividad", true) { confirmar("Eliminar actividad", "Se eliminará ${actividad.titulo} y su nota.") { repositorio?.eliminar("actividades", actividad.id) } }
        }
    }

    /** Explica el promedio acumulado y muestra sus aportes por materia y categoría. */
    private fun mostrarCalculadora(datos: RepositorioAcademico) {
        texto(contenido, "Calculadora de notas", 27f, azul, true)
        tarjeta(oscura = true) { panel ->
            texto(panel, "PROMEDIO ACUMULADO DEL CICLO", 12f, Color.rgb(222, 193, 115), true)
            texto(panel, ReglasAcademicas.notaTexto(ReglasAcademicas.calcularPromedioGlobal(datos.actividades)), 48f, Color.WHITE, true)
            texto(panel, "Escala de 0 a 10", 14f, Color.WHITE)
        }
        texto(contenido, "Cada evaluación aporta nota × porcentaje ÷ 100. Las notas pendientes aportan cero hasta registrarlas. El promedio del ciclo es la media de las materias con evaluaciones; todas tienen el mismo peso.")
        if (datos.actividades.isEmpty()) vacio("Registrá tus evaluaciones", "En cada actividad podés indicar su nota y porcentaje.")
        datos.materias.forEach { materia ->
            val actividades = datos.actividades.filter { it.materiaId == materia.id }
            if (actividades.isNotEmpty()) tarjeta { panel ->
                texto(panel, materia.nombre, 18f, azul, true)
                val promedio = ReglasAcademicas.calcularPromedio(actividades)
                texto(panel, "${ReglasAcademicas.notaTexto(promedio)} / 10", 26f, dorado, true)
                panel.addView(LinearProgressIndicator(this).apply { max = 100; progress = (promedio * 10).toInt(); setIndicatorColor(azul); trackColor = Color.rgb(232, 235, 241) }, LinearLayout.LayoutParams(-1, dp(6)))
                val peso = actividades.sumOf { it.porcentaje }
                texto(panel, "Porcentaje configurado: ${ReglasAcademicas.notaTexto(peso)} % / 100 %", 12f)
                if (peso < 100) texto(panel, "Falta asignar ${ReglasAcademicas.notaTexto(100 - peso)} % para completar la materia.", 12f, dorado)
                actividades.groupBy { it.tipo }.forEach { (tipo, grupo) -> texto(panel, "$tipo · ${ReglasAcademicas.notaTexto(grupo.sumOf { it.porcentaje })} % · Aporte ${ReglasAcademicas.notaTexto(ReglasAcademicas.calcularPromedio(grupo))}", 13f, azul) }
                boton(panel, "Registrar evaluación", true) { formularioActividad(materiaInicial = materia.id) }
            }
        }
    }

    /** Muestra apuntes de texto y sus adjuntos privados con filtro por materia. */
    private fun mostrarApuntes(datos: RepositorioAcademico) {
        texto(contenido, "Apuntes y notas", 27f, azul, true)
        texto(contenido, "Ideas de clase, documentos e imágenes por materia.")
        texto(contenido, "Los adjuntos se guardan solo en este teléfono. Exportá una copia para conservarlos fuera de la aplicación.", 12f, dorado)
        boton(contenido, "+ Nuevo apunte") { if (exigirMaterias()) formularioApunte() }
        val identificadores = listOf("") + datos.materias.map { it.id }
        val filtro = selector(contenido, "Filtrar por materia", listOf("Todas las materias") + datos.materias.map { it.nombre }, identificadores.indexOf(filtroApuntes))
        filtro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** Actualiza la materia usada para filtrar los apuntes visibles. */
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { if (filtroApuntes != identificadores[position]) { filtroApuntes = identificadores[position]; mostrarAplicacion() } }
            /** Mantiene la materia actual cuando no hay una selección nueva. */
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        val apuntes = datos.apuntes.filter { filtroApuntes.isEmpty() || it.materiaId == filtroApuntes }.reversed()
        if (apuntes.isEmpty()) vacio("Guardá tus ideas de clase", "Cada apunte queda asociado a una materia.")
        apuntes.forEach { apunte -> tarjeta { panel ->
            texto(panel, nombreMateria(apunte.materiaId).uppercase(), 11f, dorado, true)
            texto(panel, apunte.titulo, 21f, azul, true)
            texto(panel, apunte.contenido)
            apunte.adjuntos.forEach { adjunto ->
                texto(panel, "${adjunto.nombre} · ${adjunto.tamano / 1024} KB", 13f, azul)
                boton(panel, "Guardar copia de ${adjunto.nombre}", true) { adjuntoParaDescargar = adjunto; guardarArchivo.launch(adjunto.nombre) }
                boton(panel, "Quitar adjunto", true) { confirmar("Eliminar adjunto", "Se eliminará ${adjunto.nombre} de este teléfono.") { datos.eliminarAdjunto(adjunto) { aviso("Adjunto local eliminado.") } } }
            }
            boton(panel, "Editar apunte", true) { formularioApunte(apunte) }
            boton(panel, "Adjuntar imagen o PDF", true) {
                if (apunte.adjuntos.size >= 5) aviso("Podés agregar hasta cinco adjuntos por apunte.")
                else { apunteParaAdjuntar = apunte.id; elegirArchivo.launch(arrayOf("application/pdf", "image/jpeg", "image/png", "image/webp")) }
            }
            boton(panel, "Eliminar apunte", true) {
                if (apunte.adjuntos.isNotEmpty()) aviso("Quitá primero los adjuntos para eliminar el apunte.", true)
                else confirmar("Eliminar apunte", "Se eliminará ${apunte.titulo}.") { datos.eliminar("apuntes", apunte.id) }
            }
        } }
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

    /** Solicita permiso y muestra un recordatorio local de ejemplo sin usar un servidor. */
    private fun probarRecordatorio() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            probarAlDarPermiso = true
            permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Recordatorios.probar(this)) aviso("Recordatorio de prueba enviado al teléfono.")
        else aviso("Activá las notificaciones de UDB Academic en los ajustes del teléfono.", true)
    }

    /** Muestra noticias y eventos publicados desde la consola de Firebase. */
    private fun mostrarNoticias(datos: RepositorioAcademico) {
        texto(contenido, "Noticias y eventos", 27f, azul, true)
        texto(contenido, "Información académica publicada para la comunidad.")
        if (datos.noticias.isEmpty()) vacio("No hay publicaciones todavía", "Las noticias aparecerán cuando el administrador las publique.")
        datos.noticias.forEach { noticia -> tarjeta { panel ->
            texto(panel, noticia.categoria.uppercase(), 11f, dorado, true)
            texto(panel, noticia.titulo, 22f, azul, true)
            texto(panel, formatoFecha.format(Date(noticia.fecha)), 12f)
            texto(panel, noticia.descripcion)
            if (noticia.enlace.startsWith("https://")) boton(panel, "Ver información", true) { abrirEnlace(noticia.enlace) }
        } }
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

    /** Registra o edita una materia con su docente y correo de contacto. */
    private fun formularioMateria(materia: Materia? = null) {
        dialogo(if (materia == null) "Nueva materia" else "Editar materia") { panel, error ->
            val nombre = entrada(panel, "Nombre de la materia", materia?.nombre.orEmpty())
            val codigo = entrada(panel, "Código", materia?.codigo.orEmpty())
            val docente = entrada(panel, "Nombre del catedrático", materia?.docente.orEmpty())
            val correo = entrada(panel, "Correo del catedrático", materia?.correoDocente.orEmpty(), InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
            val guardar: () -> Boolean = {
                when {
                    nombre.text.toString().trim().length !in 2..120 -> { error.text = "El nombre debe tener entre 2 y 120 caracteres."; false }
                    codigo.text.toString().trim().length !in 1..30 -> { error.text = "Ingresá un código de hasta 30 caracteres."; false }
                    docente.text.toString().length > 100 -> { error.text = "El nombre del docente admite hasta 100 caracteres."; false }
                    correo.text.toString().isNotBlank() && !android.util.Patterns.EMAIL_ADDRESS.matcher(correo.text.toString().trim()).matches() -> { error.text = "Revisá el correo del docente."; false }
                    else -> { val id = materia?.id ?: repositorio!!.nuevoId(); repositorio!!.guardar("materias", id, Materia(id, nombre.text.toString().trim(), codigo.text.toString().trim().uppercase(), docente.text.toString().trim(), correo.text.toString().trim())); true }
                }
            }
            guardar
        }
    }

    /** Registra o edita una clase y valida cruces de horario. */
    private fun formularioHorario(horario: Horario? = null) {
        val datos = repositorio ?: return
        dialogo(if (horario == null) "Nueva clase" else "Editar clase") { panel, error ->
            val materia = selector(panel, "Materia", datos.materias.map { it.nombre }, datos.materias.indexOfFirst { it.id == horario?.materiaId })
            val dia = selector(panel, "Día", listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes"), (horario?.dia ?: diaSeleccionado) - 1)
            val inicio = entrada(panel, "Hora inicial (HH:mm)", horario?.horaInicio ?: "07:00", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
            val fin = entrada(panel, "Hora final (HH:mm)", horario?.horaFin ?: "08:00", InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME)
            val aula = entrada(panel, "Aula o laboratorio", horario?.aula.orEmpty())
            val guardar: () -> Boolean = {
                val nuevo = Horario(horario?.id ?: datos.nuevoId(), datos.materias[materia.selectedItemPosition].id, dia.selectedItemPosition + 1, inicio.text.toString().trim(), fin.text.toString().trim(), aula.text.toString().trim())
                val problema = ReglasAcademicas.validarHorario(nuevo, datos.horarios)
                when {
                    problema != null -> { error.text = problema; false }
                    nuevo.aula.length !in 1..100 -> { error.text = "Ingresá un aula de hasta 100 caracteres."; false }
                    else -> { datos.guardar("horarios", nuevo.id, nuevo); true }
                }
            }
            guardar
        }
    }

    /** Registra o edita una evaluación con su fecha, nota y ponderación. */
    private fun formularioActividad(actividad: Actividad? = null, materiaInicial: String = "") {
        val datos = repositorio ?: return
        dialogo(if (actividad == null) "Nueva actividad" else "Editar actividad") { panel, error ->
            val titulo = entrada(panel, "Título", actividad?.titulo.orEmpty())
            val materia = selector(panel, "Materia", datos.materias.map { it.nombre }, datos.materias.indexOfFirst { it.id == (actividad?.materiaId ?: materiaInicial) })
            val tipos = listOf("Tarea", "Laboratorio", "Parcial", "Proyecto", "Otro")
            val tipo = selector(panel, "Tipo de evaluación", tipos, tipos.indexOf(actividad?.tipo))
            val descripcion = entrada(panel, "Descripción", actividad?.descripcion.orEmpty(), multilinea = true)
            val fecha = Calendar.getInstance().apply { timeInMillis = actividad?.fechaEntrega ?: (System.currentTimeMillis() + 86400000) }
            lateinit var fechaBoton: MaterialButton
            fechaBoton = boton(panel, "Entrega: ${formatoFecha.format(fecha.time)}", true) {
                DatePickerDialog(this, { _, anio, mes, dia ->
                    fecha.set(anio, mes, dia)
                    TimePickerDialog(this, { _, hora, minuto -> fecha.set(Calendar.HOUR_OF_DAY, hora); fecha.set(Calendar.MINUTE, minuto); fecha.set(Calendar.SECOND, 0); fecha.set(Calendar.MILLISECOND, 0); fechaBoton.text = "Entrega: ${formatoFecha.format(fecha.time)}" }, fecha.get(Calendar.HOUR_OF_DAY), fecha.get(Calendar.MINUTE), true).show()
                }, fecha.get(Calendar.YEAR), fecha.get(Calendar.MONTH), fecha.get(Calendar.DAY_OF_MONTH)).show()
            }
            val porcentaje = entrada(panel, "Porcentaje de la materia (0 a 100)", actividad?.porcentaje?.toString() ?: "0", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val nota = entrada(panel, "Nota (0 a 10, opcional)", actividad?.nota?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
            val estado = selector(panel, "Estado", listOf("Pendiente", "Entregada"), if (actividad?.estado == "entregado") 1 else 0)
            val guardar: () -> Boolean = {
                val valorPorcentaje = porcentaje.text.toString().replace(',', '.').toDoubleOrNull()
                val textoNota = nota.text.toString().trim()
                val valorNota = textoNota.replace(',', '.').toDoubleOrNull()
                if (valorPorcentaje == null || (textoNota.isNotEmpty() && valorNota == null)) { error.text = "Revisá la nota y el porcentaje; deben ser números válidos."; false }
                else {
                    val nueva = Actividad(actividad?.id ?: datos.nuevoId(), datos.materias[materia.selectedItemPosition].id, titulo.text.toString().trim(), descripcion.text.toString().trim(), tipos[tipo.selectedItemPosition], fecha.timeInMillis, if (estado.selectedItemPosition == 1) "entregado" else "pendiente", valorNota, valorPorcentaje)
                    val problema = ReglasAcademicas.validarEvaluacion(nueva, datos.actividades)
                    when {
                        nueva.titulo.length !in 2..150 -> { error.text = "El título debe tener entre 2 y 150 caracteres."; false }
                        nueva.descripcion.length > 5000 -> { error.text = "La descripción admite hasta 5,000 caracteres."; false }
                        problema != null -> { error.text = problema; false }
                        else -> { datos.guardar("actividades", nueva.id, nueva); true }
                    }
                }
            }
            guardar
        }
    }

    /** Redacta un apunte de texto vinculado a una materia. */
    private fun formularioApunte(apunte: Apunte? = null) {
        val datos = repositorio ?: return
        dialogo(if (apunte == null) "Nuevo apunte" else "Editar apunte") { panel, error ->
            val titulo = entrada(panel, "Título del apunte", apunte?.titulo.orEmpty())
            val materia = selector(panel, "Materia", datos.materias.map { it.nombre }, datos.materias.indexOfFirst { it.id == apunte?.materiaId })
            val contenido = entrada(panel, "Contenido", apunte?.contenido.orEmpty(), multilinea = true)
            texto(panel, "Guardá el apunte para poder adjuntar imágenes o documentos PDF.", 12f)
            val guardar: () -> Boolean = {
                val nuevo = Apunte(apunte?.id ?: datos.nuevoId(), datos.materias[materia.selectedItemPosition].id, titulo.text.toString().trim(), contenido.text.toString().trim(), apunte?.adjuntos.orEmpty())
                if (nuevo.titulo.length !in 2..150 || nuevo.contenido.length !in 1..20000) { error.text = "Ingresá un título de 2 a 150 caracteres y contenido de hasta 20,000 caracteres."; false }
                else { datos.guardar("apuntes", nuevo.id, nuevo); true }
            }
            guardar
        }
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

    /** Valida los metadatos del archivo seleccionado y lo adjunta al apunte existente. */
    private fun procesarAdjunto(uri: Uri) {
        val datos = repositorio ?: return
        val apunte = datos.apuntes.find { it.id == apunteParaAdjuntar } ?: run { aviso("Volvé a seleccionar el apunte."); return }
        val tipo = contentResolver.getType(uri).orEmpty()
        var nombre = "archivo"
        var tamano = -1L
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { consulta ->
            if (consulta.moveToFirst()) { nombre = consulta.getString(0); if (!consulta.isNull(1)) tamano = consulta.getLong(1) }
        }
        if (tipo !in listOf("application/pdf", "image/jpeg", "image/png", "image/webp") || (tamano > 10485760 || tamano == 0L) || nombre.length !in 1..180) { aviso("Elegí un PDF o imagen de hasta 10 MB con un nombre de hasta 180 caracteres.", true); return }
        aviso("Guardando archivo en el teléfono…")
        datos.guardarAdjuntoLocal(apunte.id, uri, nombre, tipo) { aviso("Adjunto guardado en este dispositivo.") }
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

    /** Prepara una clase semanal en el calendario para que el estudiante confirme su incorporación. */
    private fun agregarAlCalendario(horario: Horario) {
        val inicio = Calendar.getInstance()
        val diaActual = (inicio.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        inicio.add(Calendar.DAY_OF_YEAR, (horario.dia - diaActual + 7) % 7)
        val horaInicial = horario.horaInicio.split(":")
        inicio.set(Calendar.HOUR_OF_DAY, horaInicial[0].toInt())
        inicio.set(Calendar.MINUTE, horaInicial[1].toInt())
        inicio.set(Calendar.SECOND, 0)
        inicio.set(Calendar.MILLISECOND, 0)
        if (inicio.timeInMillis < System.currentTimeMillis()) inicio.add(Calendar.DAY_OF_YEAR, 7)
        val fin = (inicio.clone() as Calendar).apply {
            val horaFinal = horario.horaFin.split(":")
            set(Calendar.HOUR_OF_DAY, horaFinal[0].toInt())
            set(Calendar.MINUTE, horaFinal[1].toInt())
        }
        val dias = listOf("MO", "TU", "WE", "TH", "FR")
        val evento = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, nombreMateria(horario.materiaId))
            .putExtra(CalendarContract.Events.EVENT_LOCATION, horario.aula)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, inicio.timeInMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, fin.timeInMillis)
            .putExtra(CalendarContract.Events.RRULE, "FREQ=WEEKLY;BYDAY=${dias[horario.dia - 1]}")
        try { startActivity(evento) }
        catch (_: android.content.ActivityNotFoundException) { aviso("Instalá una aplicación de calendario para agregar tus clases.", true) }
    }

    /** Obtiene el nombre de la materia asociada a un registro. */
    private fun nombreMateria(id: String): String = repositorio?.materias?.find { it.id == id }?.nombre ?: "Materia"

    /** Impide crear registros dependientes cuando todavía no hay materias. */
    private fun exigirMaterias(): Boolean {
        if (repositorio?.materias.isNullOrEmpty()) { aviso("Primero agregá una materia en Horario.", true); return false }
        return true
    }

    /** Explica los estados vacíos de cada sección. */
    private fun vacio(titulo: String, detalle: String) { tarjeta { panel -> texto(panel, titulo, 17f, azul, true); texto(panel, detalle) } }

    /** Solicita confirmación antes de eliminar datos de la aplicación. */
    private fun confirmar(titulo: String, mensaje: String, accion: () -> Unit) { MaterialAlertDialogBuilder(this).setTitle(titulo).setMessage(mensaje).setNegativeButton("Cancelar", null).setPositiveButton("Eliminar") { _, _ -> accion() }.show() }

    /** Abre enlaces seguros o el cliente de correo del dispositivo. */
    private fun abrirEnlace(enlace: String) {
        if (!enlace.startsWith("https://") && !enlace.startsWith("mailto:")) return
        try { startActivity(Intent(if (enlace.startsWith("mailto:")) Intent.ACTION_SENDTO else Intent.ACTION_VIEW, Uri.parse(enlace))) }
        catch (_: android.content.ActivityNotFoundException) { aviso("No hay una aplicación disponible para abrir este enlace.", true) }
    }

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
