package com.sergio.eva3sergioargel

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberImagePainter
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.sergio.eva3sergioargel.ui.theme.Eva3SergioArgelTheme
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.time.LocalDateTime

enum class Pantalla{
    FORM,
    CAMARA
}

class AppVM : ViewModel() {
    val pantallaActual = mutableStateOf(Pantalla.FORM)
    var onPermisoCamaraOk:()-> Unit = {}
    var permisosUbicacionOk:() -> Unit = {}

    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)

}

class FormRegistroVM : ViewModel(){
    /////////////////////////////
    val nombre = mutableStateOf("")
    val fotos = mutableStateOf<List<Uri>?>(null)
}

class MainActivity : ComponentActivity() {

    val camaraVM:AppVM by viewModels()
    val formRegistroVM:FormRegistroVM by viewModels()

    lateinit var cameraController: LifecycleCameraController

    val lanzadorPermisos = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){
        if ((it[android.Manifest.permission.CAMERA]?:false) or
        (it[android.Manifest.permission.WRITE_EXTERNAL_STORAGE]?:false)){
            camaraVM.onPermisoCamaraOk()
        }
        if (
            (it[android.Manifest.permission.ACCESS_FINE_LOCATION]?:false) or
            (it[android.Manifest.permission.ACCESS_COARSE_LOCATION]?:false)
        ){
            camaraVM.permisosUbicacionOk()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        setContent {
            AppUI(lanzadorPermisos, cameraController)
        }
    }
}

@Composable
fun AppUI(lanzadorPermisos: ActivityResultLauncher<Array<String>>,
          cameraController: LifecycleCameraController
){
    val appVM:AppVM = viewModel()

    when(appVM.pantallaActual.value){
        Pantalla.FORM -> {
            PantallaFormUI(appVM, lanzadorPermisos)
        }
        Pantalla.CAMARA -> {
            PantallaCamaraUI(lanzadorPermisos,cameraController)
        }
    }
}
//fun uri2imageBitmap(uri:Uri, contexto:Context) =
//    BitmapFactory.decodeStream(
//        contexto.contentResolver.openInputStream(uri)
//    ).asImageBitmap()
fun uri2imageBitmap(uri: Uri, contexto: Context): ImageBitmap {
    val options = BitmapFactory.Options()
    options.inSampleSize = 2 //
    val bitmap =
        BitmapFactory.decodeStream(contexto.contentResolver.openInputStream(uri), null, options)
    if (bitmap != null) {
        return bitmap.asImageBitmap()
    }
    return TODO("Provide the return value")
}

class FaltaPermisosException(mensaje:String): Exception(mensaje)


fun conseguirUbicacion(contexto: Context, onSucces:(ubicacion: Location) -> Unit){
    try {
        val servicio = LocationServices.getFusedLocationProviderClient(contexto)
        val tarea = servicio.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            null
        )
        tarea.addOnSuccessListener {
            onSucces(it)
        }

    }catch (se:SecurityException){
        throw FaltaPermisosException("Sin permisos de ubicación")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI(appVM:AppVM, lanzadorPermisos:ActivityResultLauncher<Array<String>>){
    val contexto = LocalContext.current
    val appVM: AppVM = viewModel()
    val formRegistroVM: FormRegistroVM = viewModel()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        TitledText("Atractivo Turístico")
        Spacer(modifier = Modifier.height(10.dp))
        TextField(
            value = formRegistroVM.nombre.value,
            onValueChange = { formRegistroVM.nombre.value = it },
            label = { Text("Nombre Lugar") },
            trailingIcon = {
                if (formRegistroVM.nombre.value.isNotEmpty()) {
                    Icon(Icons.Default.Check, contentDescription = "Nombre correcto")
                }
            }
        )
        Spacer(modifier = Modifier.height(30.dp))
        Row() {
            //Botón Foto
            Button(
                modifier = Modifier.width(170.dp),
                onClick = {
                    appVM.pantallaActual.value = Pantalla.CAMARA
                }) {
                Icon(Icons.Default.AccountBox, contentDescription = "Tomar Foto")
                Text("Foto")
            }
            //Botón Ubicación
            Button(
                modifier = Modifier.width(170.dp),
                onClick = {
                    // Lógica para tomar ubicación
                    appVM.permisosUbicacionOk = {
                        conseguirUbicacion(contexto){
                            appVM.latitud.value = it.latitude
                            appVM.longitud.value = it.longitude
                        }
                    }

                    lanzadorPermisos.launch(
                        arrayOf(
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                }
            ) {
                Icon(Icons.Default.Place, contentDescription = "Tomar Ubicación")
                Text("Ubicación")
            }
        }

        formRegistroVM.fotos.value?.let { fotos ->
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                if (fotos.size > 2) {
                    item {
                        Icon(
                            modifier = Modifier
                                .width(30.dp)
                                .padding(2.dp),
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Flecha izquierda"
                        )
                    }
                }

                items(fotos) { fotoUri ->
                    Image(
                        modifier = Modifier
                            .width(150.dp) // Ajusta el ancho de cada elemento del carrusel según tus necesidades
                            .padding(8.dp), // Añade un espacio entre las imágenes
                        painter = rememberImagePainter(data = fotoUri),
                        contentDescription = "Imagen capturada desde CamaraX"
                    )
                }

                if (fotos.size > 2) {
                    item {
                        Icon(
                            modifier = Modifier
                                .width(30.dp)
                                .padding(2.dp),
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "Flecha derecha"
                        )
                    }
                }
            }
        }
        if(appVM.latitud.value != 0.0 && appVM.longitud.value != 0.0) {
            //Mapa Ubicación
            TitledText("Mapa")
            Spacer(Modifier.height(25.dp))

            AndroidView(
                factory = {
                    MapView(it).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        org.osmdroid.config.Configuration.getInstance().userAgentValue = contexto.packageName
                        controller.setZoom(15.0)
                    }
                }, update = {
                    it.overlays.removeIf { true }
                    it.invalidate()

                    val geoPoint = GeoPoint(appVM.latitud.value, appVM.longitud.value)
                    it.controller.animateTo(geoPoint)

                    val marcador = Marker(it)
                    marcador.position = geoPoint
                    marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    it.overlays.add(marcador)


                }
            )
//            Text("Lat: ${appVM.latitud.value} Long: ${appVM.longitud.value}")
        }




    }
}

fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)


fun crearArchivoImagenPublico(contexto: Context): File {
    val directorioFotosPublico = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val nombreArchivo = "IMG_${generarNombreSegunFechaHastaSegundo()}.jpg"
    val archivo = File(directorioFotosPublico, nombreArchivo)

    // Agregar la imagen a la base de datos de medios
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, nombreArchivo)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
    }

    val contentResolver = contexto.contentResolver
    val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

    try {
        // Copiar la foto al directorio público
        val inputStream = contexto.contentResolver.openInputStream(imageUri!!)
        val outputStream = archivo.outputStream()
        inputStream?.copyTo(outputStream)
        inputStream?.close()
        outputStream.close()
    } catch (e: IOException) {
        e.printStackTrace()
    }

    return archivo
}

fun capturarFotografia(
    formRegistroVM: FormRegistroVM,
    cameraController: LifecycleCameraController,
    archivo: File,
    contexto: Context,
    onImagenGuardada: (Uri) -> Unit
) {
    val opciones = ImageCapture.OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(
        opciones,
        ContextCompat.getMainExecutor(contexto),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let {uri->
                    val fotos = formRegistroVM.fotos.value?.toMutableList() ?: mutableListOf()
                    fotos.add(uri)
                    formRegistroVM.fotos.value = fotos
                    onImagenGuardada(uri)
//                    onImagenGuardada(it)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                // Maneja el error
            }
        }
    )
}



@Composable
fun PantallaCamaraUI(lanzadorPermisos: ActivityResultLauncher<Array<String>>,
                     cameraController: LifecycleCameraController

){
    val contexto = LocalContext.current
    val formRegistroVM:FormRegistroVM = viewModel()
    val appVM:AppVM = viewModel()

    lanzadorPermisos.launch(arrayOf(android.Manifest.permission.CAMERA))
    lanzadorPermisos.launch(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE))

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        })
    Button(onClick = {
        val archivoFoto = crearArchivoImagenPublico(contexto)
        capturarFotografia(formRegistroVM,cameraController, archivoFoto, contexto) { uri ->
//            formRegistroVM.fotos.value = uri
            appVM.pantallaActual.value = Pantalla.FORM
        }

    }){
        Icon(Icons.Default.AccountBox, contentDescription = "Capturar")
        Text("Capturar")
    }
}

@Composable
fun TitledText(text: String) {
    Text(
        text = text,
        fontSize = 16.sp, // Tamaño de fuente deseado
        fontWeight = FontWeight.Bold, // Negrita
        color = Color.Black, // Color del texto
        modifier = Modifier
            .height(40.dp) // Altura deseada
            .padding(vertical = 8.dp, horizontal = 60.dp)
    )
}