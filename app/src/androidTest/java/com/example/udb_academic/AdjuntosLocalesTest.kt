package com.example.udb_academic

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.udb_academic.datos.AdjuntosLocales
import com.example.udb_academic.modelo.Adjunto
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
}
