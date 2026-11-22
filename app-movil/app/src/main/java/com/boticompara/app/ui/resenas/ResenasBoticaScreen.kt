package com.boticompara.app.ui.resenas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.boticompara.app.data.remote.dto.ResenaResponse
import com.boticompara.app.data.remote.dto.ResenasResponse
import com.boticompara.app.ui.components.BotiOutlinedButton
import com.boticompara.app.ui.components.BotiTonalButton
import com.boticompara.app.ui.components.EstadoCargando
import com.boticompara.app.ui.components.EstadoError
import com.boticompara.app.ui.components.EstadoVacio
import com.boticompara.app.ui.components.IconoCircular
import com.boticompara.app.ui.reservas.FechaLima
import com.boticompara.app.ui.theme.Dimens

const val RUTA_RESENAS = "resenas/{boticaId}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResenasBoticaScreen(
    boticaId: Long,
    navController: NavController,
    viewModel: ResenasBoticaViewModel = viewModel()
) {
    val vm = viewModel
    val snackbarHostState = remember { SnackbarHostState() }
    var mostrarFormulario by remember { mutableStateOf(false) }

    LaunchedEffect(boticaId) { vm.cargar(boticaId) }

    LaunchedEffect(vm.sesionVencida) {
        if (vm.sesionVencida) {
            navController.navigate("login") {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    LaunchedEffect(vm.guardado) {
        if (vm.guardado) {
            mostrarFormulario = false
            snackbarHostState.showSnackbar("Reseña guardada")
            vm.guardadoMostrado()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("Reseñas de la botica", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.mensajeFormularioMostrado()
                    mostrarFormulario = true
                },
                icon = {
                    Icon(
                        imageVector = if (vm.datos?.miResena != null) Icons.Rounded.Edit
                        else Icons.Rounded.StarBorder,
                        contentDescription = null
                    )
                },
                text = {
                    Text(if (vm.datos?.miResena != null) "Editar mi reseña" else "Calificar botica")
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = Dimens.Space20)
        ) {
            when {
                vm.isLoading -> EstadoCargando("Cargando reseñas...")

                vm.datos == null && vm.mensajeError != null ->
                    EstadoError(vm.mensajeError!!) { vm.cargar(boticaId) }

                vm.datos != null -> ContenidoResenas(
                    datos = vm.datos!!,
                    cargandoMas = vm.cargandoMas,
                    mensajeErrorPaginacion = vm.mensajeErrorPaginacion,
                    onVerMas = { vm.cargarSiguiente() }
                )
            }
        }
    }

    if (mostrarFormulario) {
        HojaResena(
            miResena = vm.datos?.miResena,
            guardando = vm.guardando,
            mensajeError = vm.mensajeFormulario,
            onGuardar = { estrellas, comentario -> vm.guardar(estrellas, comentario) },
            onDismiss = { mostrarFormulario = false }
        )
    }
}

@Composable
private fun ContenidoResenas(
    datos: ResenasResponse,
    cargandoMas: Boolean,
    mensajeErrorPaginacion: String?,
    onVerMas: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Dimens.Space12),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = Dimens.Space32 + Dimens.Space48
        )
    ) {
        item { TarjetaResumen(datos) }

        if (datos.total == 0 || datos.resenas.isEmpty()) {
            item {
                EstadoVacio(
                    icono = Icons.Rounded.StarBorder,
                    texto = "Todavía no hay reseñas. Sé el primero en calificar esta botica."
                )
            }
        } else {
            items(datos.resenas, key = { it.id }) { resena -> TarjetaResena(resena) }
            item {
                Spacer(Modifier.height(Dimens.Space4))
                when {
                    cargandoMas -> Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(Dimens.IconMedium),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(Dimens.Space12))
                        Text("Cargando más reseñas...", style = MaterialTheme.typography.bodyMedium)
                    }

                    mensajeErrorPaginacion != null -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            mensajeErrorPaginacion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(Dimens.Space8))
                        BotiOutlinedButton(text = "Reintentar", onClick = onVerMas)
                    }

                    ReglasResena.hayMasPaginas(datos) -> BotiTonalButton(
                        text = "Ver más reseñas",
                        onClick = onVerMas,
                        icon = Icons.Rounded.StarBorder,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(Dimens.Space8))
            }
        }
    }
}

@Composable
private fun TarjetaResumen(datos: ResenasResponse) {
    val promedio = ReglasResena.formatearPromedio(datos.promedio)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(Dimens.IconXLarge)
                )
                Spacer(Modifier.width(Dimens.Space16))
                Column {
                    Text(
                        promedio ?: "Sin reseñas",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (promedio != null) {
                        Text(
                            ReglasResena.textoCantidad(datos.total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
            if (promedio != null) {
                Spacer(Modifier.height(Dimens.Space12))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                Spacer(Modifier.height(Dimens.Space12))
                Estrellas(
                    estrellas = ReglasResena.estrellasDePromedio(datos.promedio),
                    tamano = Dimens.IconMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    colorInactivo = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun TarjetaResena(resena: ResenaResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(Dimens.Space20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconoCircular(icono = Icons.Rounded.Edit, tamano = 40.dp)
                Spacer(Modifier.width(Dimens.Space12))
                Column(modifier = Modifier.weight(1f)) {
                    Text(resena.autor, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Estrellas(estrellas = resena.estrellas)
                }
                Text(
                    FechaLima.formatear(resena.creadoEn),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!resena.comentario.isNullOrBlank()) {
                Spacer(Modifier.height(Dimens.Space12))
                Text(resena.comentario, style = MaterialTheme.typography.bodyMedium)
            }

            if (!resena.respuesta.isNullOrBlank()) {
                Spacer(Modifier.height(Dimens.Space12))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(Dimens.Space16)) {
                        Text(
                            "Respuesta de la botica",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(Dimens.Space4))
                        Text(
                            resena.respuesta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        if (!resena.respondidaEn.isNullOrBlank()) {
                            Spacer(Modifier.height(Dimens.Space8))
                            Text(
                                FechaLima.formatear(resena.respondidaEn),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Estrellas(
    estrellas: Int,
    tamano: Dp = Dimens.IconSmall,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    colorInactivo: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.semantics {
            contentDescription = ReglasResena.textoEstrellas(estrellas)
        }
    ) {
        (1..5).forEach { valor ->
            Icon(
                imageVector = if (valor <= estrellas) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (valor <= estrellas) color else colorInactivo,
                modifier = Modifier.size(tamano)
            )
        }
    }
}
