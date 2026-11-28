package com.boticompara.app.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

/** viewModelScope usa Dispatchers.Main: en las pruebas es un despachador con reloj virtual (runTest lo comparte). */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class PruebaConMain {
    @Before
    fun prepararMain() {
        Dispatchers.setMain(nuevoDespachador())
    }

    @After
    fun limpiarMain() {
        Dispatchers.resetMain()
    }
}
