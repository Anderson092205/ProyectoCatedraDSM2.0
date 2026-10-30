package com.example.udb_academic

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class RecorridoAplicacionTest {
    /** Recorre las pantallas y prueba la creación de una materia con datos ficticios. */
    @Test fun recorrerAplicacionYCrearMateria() {
        PreparacionEmulador.esperarInterfazDelSistema()
        ActivityScenario.launch(ActividadPrincipal::class.java).use {
            capturar("01_acceso")
            onView(withText("Ver demostración con datos de ejemplo")).perform(scrollTo(), click())
            onView(withText("Clases de hoy")).check(matches(isDisplayed()))
            capturar("02_inicio")
            onView(allOf(withText("Horario"), isDisplayed())).perform(click())
            capturar("03_horario")
            onView(withText("+ Agregar materia")).perform(scrollTo(), click())
            onView(withHint("Nombre de la materia")).perform(replaceText("Materia de prueba"), closeSoftKeyboard())
            onView(withHint("Código")).perform(replaceText("PRU001"), closeSoftKeyboard())
            onView(withText("Guardar")).perform(click())
            onView(allOf(withText("Actividades"), isDisplayed())).perform(click())
            capturar("04_actividades")
            onView(withText("Calculadora de notas")).perform(scrollTo(), click())
            onView(withText("PROMEDIO ACUMULADO DEL CICLO")).check(matches(isDisplayed()))
            capturar("05_calculadora")
            onView(allOf(withText("Apuntes"), isDisplayed())).perform(click())
            capturar("06_apuntes")
            onView(allOf(withText("Perfil"), isDisplayed())).perform(click())
            onView(withText("Estudiante de ejemplo")).check(matches(isDisplayed()))
            capturar("07_perfil")
            onView(withText("Noticias")).perform(click())
            capturar("08_noticias")
        }
    }

    /** Guarda una captura del emulador para revisar la presentación de cada pantalla. */
    private fun capturar(nombre: String) {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        instrumentacion.waitForIdleSync()
        val carpeta = File(instrumentacion.targetContext.getExternalFilesDir(null), "capturas").apply { mkdirs() }
        FileOutputStream(File(carpeta, "$nombre.png")).use { salida -> instrumentacion.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, salida) }
    }
}
