package com.example.udb_academic

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

object PreparacionEmulador {
    /** Cierra únicamente el aviso de demora de System UI del emulador usando su opción de esperar. */
    fun esperarInterfazDelSistema() {
        val automatizacion = InstrumentationRegistry.getInstrumentation().uiAutomation
        val raiz = automatizacion.rootInActiveWindow ?: return
        if (raiz.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty()) return
        var boton: AccessibilityNodeInfo? = raiz.findAccessibilityNodeInfosByText("Wait").find { it.text?.toString() == "Wait" }
        while (boton != null && !boton.isClickable) boton = boton.parent
        check(boton?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) { "El emulador tiene su interfaz del sistema bloqueada." }
        repeat(20) {
            val actual = automatizacion.rootInActiveWindow
            if (actual != null && actual.findAccessibilityNodeInfosByText("System UI isn't responding").isEmpty()) return
            SystemClock.sleep(100)
        }
        error("La interfaz del sistema del emulador no respondió.")
    }
}
