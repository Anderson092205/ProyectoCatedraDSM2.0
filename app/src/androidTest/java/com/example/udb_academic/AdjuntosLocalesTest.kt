package com.example.udb_academic

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.udb_academic.datos.AdjuntosLocales
import com.example.udb_academic.modelo.Adjunto
import com.example.udb_academic.notificaciones.Recordatorios
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AdjuntosLocalesTest {
    /** Comprueba guardado, persistencia, exportación y separación entre cuentas locales. */
    @Test fun guardarExportarYSepararArchivos() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "prueba_${UUID.randomUUID()}"
        val almacen = AdjuntosLocales(contexto, id)
        val origen = File(contexto.cacheDir, "origen_$id.pdf").apply { writeText("%PDF-1.4\nDocumento de prueba local") }
        val destino = File(contexto.cacheDir, "copia_$id.pdf")
        try {
            val adjunto = almacen.guardar("apunte", Uri.fromFile(origen), "ejemplo.pdf", "application/pdf")
            assertEquals(1, AdjuntosLocales(contexto, id).listar("apunte").size)
            assertEquals(origen.length(), adjunto.tamano)
            almacen.exportar(adjunto, Uri.fromFile(destino))
            assertArrayEquals(origen.readBytes(), destino.readBytes())
            try { AdjuntosLocales(contexto, "otro_$id").exportar(adjunto, Uri.fromFile(destino)); fail("Otra cuenta no debe acceder al archivo") } catch (_: IllegalStateException) { }
            try { almacen.exportar(Adjunto(ruta = "local:../archivo"), Uri.fromFile(destino)); fail("Una ruta inválida debe rechazarse") } catch (_: IllegalArgumentException) { }
            almacen.eliminar(adjunto)
            assertTrue(almacen.listar("apunte").isEmpty())
        } finally {
            almacen.listar("apunte").forEach { almacen.eliminar(it) }
            origen.delete(); destino.delete()
        }
    }

    /** Rechaza archivos demasiado grandes y limita cada apunte a cinco adjuntos. */
    @Test fun limitarCantidadYTamano() {
        val contexto = InstrumentationRegistry.getInstrumentation().targetContext
        val id = "limites_${UUID.randomUUID()}"
        val almacen = AdjuntosLocales(contexto, id)
        val origen = File(contexto.cacheDir, "limites_$id.pdf")
        try {
            origen.writeBytes(ByteArray(10485761))
            try { almacen.guardar("apunte", Uri.fromFile(origen), "grande.pdf", "application/pdf"); fail("Un archivo mayor de 10 MB debe rechazarse") } catch (_: IllegalArgumentException) { }
            assertTrue(almacen.listar("apunte").isEmpty())
            origen.writeText("%PDF-1.4\nEjemplo")
            repeat(5) { almacen.guardar("apunte", Uri.fromFile(origen), "ejemplo.pdf", "application/pdf") }
            try { almacen.guardar("apunte", Uri.fromFile(origen), "sexto.pdf", "application/pdf"); fail("No se admite un sexto adjunto") } catch (_: IllegalStateException) { }
            assertEquals(5, almacen.listar("apunte").size)
        } finally { almacen.listar("apunte").forEach { almacen.eliminar(it) }; origen.delete() }
    }

    /** Verifica que se publique una notificación de prueba sin sesión ni servicios en la nube. */
    @Test fun mostrarRecordatorioSinServidor() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val contexto = instrumentacion.targetContext
        if (Build.VERSION.SDK_INT >= 33) instrumentacion.uiAutomation.executeShellCommand("pm grant ${contexto.packageName} ${Manifest.permission.POST_NOTIFICATIONS}").use { descriptor -> android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() } }
        assertTrue(Recordatorios.probar(contexto))
        val gestor = contexto.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val limite = android.os.SystemClock.elapsedRealtime() + 3000
        while (gestor.activeNotifications.none { it.tag == "demostracion_local" } && android.os.SystemClock.elapsedRealtime() < limite) android.os.SystemClock.sleep(100)
        assertTrue(gestor.activeNotifications.any { it.tag == "demostracion_local" })
        gestor.cancel("demostracion_local", 1)
    }
}
