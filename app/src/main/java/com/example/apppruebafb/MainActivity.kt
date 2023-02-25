package com.example.apppruebafb

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.icu.text.SimpleDateFormat
import android.location.LocationManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.apppruebafb.databinding.ActivityMainBinding
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class MainActivity : AppCompatActivity() {
    private lateinit var binding:ActivityMainBinding
    //Variable firestore -------------------------------------------------------------------
    val db=Firebase.firestore
    var reg:MutableMap<String,String> = HashMap()//Hash map que contiene los datos.
    //var ROL:String? = "-"
    //var Modelo:String? = Build.MODEL
    var nombre_experimento:String?=""
    var serie:String?=""
    //Variables ble-------------------------------------------------------------------------

    private val scanConfig = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
    private var scanning = false
    var SCAN_PERIOD:Long = 5000
    var FREQ:Long=60000
    var duracion_ins:Long=300000 //5 minutos
    private val handler = Handler()
    private var bleSCAN: BluetoothLeScanner?= null
    //SCAN CALLBACK BLE --------------------------------------------------------------------

    private val bleScanCallback = object:ScanCallback(){
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {

            if (result?.device?.address.toString()=="60:77:71:8E:69:B9" || result?.device?.address.toString() == "60:77:71:8E:72:85") {
                if(result?.rssi!! > -64){
                    reg["Nombre"] = result?.device?.name.toString()
                    reg["Fecha"] = horaActual().toString()
                    reg["Rssi"] = result?.rssi.toString()
                    reg["Rol"] = datos_part?.get("Rol").toString()
                    reg["Distancia"] = "< 1m"

                    db.collection("registros").document(datos_part?.get("MAC").toString()!!).collection(nombre_experimento!!).add(reg)//guarda cada registro en firebase mediante una colleción llamada registros, que contiene
                }
                Log.d("ESCANER", "Dispositivo: ${result?.device?.name}")

                //otra documentos según el id de cada dispositivo. Luego contienen colecciones con los registros de cada instancia.

            }
        }
    }
    //Variable timer--------------------------------------------------------------------------------------
    private lateinit var timer:CountDownTimer
    //--------------------------------------------------------------------------------------
    //Permisos app en tiempo de ejecución-----------------------------------------------------------------
    val permissionRequest:MutableList<String> = ArrayList()
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private fun requestPermision(){
        locationPGranted = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)== PackageManager.PERMISSION_GRANTED
        btPGranted = ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH)== PackageManager.PERMISSION_GRANTED
        btConGranted = ContextCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT)== PackageManager.PERMISSION_GRANTED
        if (!locationPGranted){
            permissionRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if(!btPGranted){
            permissionRequest.add(Manifest.permission.BLUETOOTH)
        }
        if(!btConGranted){
            permissionRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if(!btScanPGranted){
            permissionRequest.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if(!lmac){
            permissionRequest.add(Manifest.permission.ACCESS_WIFI_STATE)
        }

        if (permissionRequest.isNotEmpty()){
            permissionLauncher.launch(permissionRequest.toTypedArray())
        }
    }
    //Variables -------------------------------------------------------------------------------------
    private var locationPGranted = false
    private var btPGranted = false
    private var btScanPGranted=false
    private var btConGranted=false
    private var lmac=false
    var datos_part: Map<String, Any>? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)



        val serial = getDeviceIdentifier(this)//Identificador del dispositivo.
        binding.tvIdentificador.text=serial
        serie=serial

        //Bluetooth config y permisos.
        val bluetoothManager: BluetoothManager = applicationContext.getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bleAdaptador: BluetoothAdapter? = bluetoothManager.adapter
        val locationMngr = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = locationMngr.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationMngr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        //solicitar al usuario que encienda bluetooth y localización para que no falle la app.
        if (bleAdaptador==null){
            //El dispositivo no posee bt.
            binding.tvMensaje.text="El dispositivo no es compatible con ble..."
            binding.tvBtStatus.setTextColor(Color.RED)
        }
        if (!bleAdaptador!!.isEnabled) {
            //SI el bt se encuentra apagado, solicitar al usuario que lo encienda.
            val activarBT = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result->
                if(result.resultCode== Activity.RESULT_OK){
                    //El usuario ha activado bt
                    binding.tvBtStatus.text="BT Activado"
                    binding.tvBtStatus.setTextColor(Color.GREEN)
                }else{
                    //El usuario no ha activado bt.
                    binding.tvBtStatus.text="BT Desactivado"
                    binding.tvBtStatus.setTextColor(Color.RED)
                }
            }
            val activarBTintento=Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activarBT.launch(activarBTintento)

        }else{
            binding.tvBtStatus.text="BT Activado"
            binding.tvBtStatus.setTextColor(Color.GREEN)
        }



        if (locationMngr==null){
            //El dispositivo no tiene disponible servicio de ubicación
            binding.tvGpsStatus.text="GPS No disponible"
            binding.tvGpsStatus.setTextColor(Color.RED)
        }else{
            if (!isLocationEnabled){
                val activarUbIntento = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(activarUbIntento)
                val isLocationEnabled = locationMngr.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationMngr.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                if (isLocationEnabled){
                    binding.tvGpsStatus.text="GPS Activado"
                    binding.tvGpsStatus.setTextColor(Color.GREEN)
                }else{
                    binding.tvGpsStatus.text="GPS Desactivado"
                    binding.tvGpsStatus.setTextColor(Color.RED)
                }
            }else{
                //El usuario no activó la ubicación.-
                binding.tvGpsStatus.text="GPS Activado"
                binding.tvGpsStatus.setTextColor(Color.GREEN)
            }
        }


        permissionLauncher=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
                permisos->
            locationPGranted = permisos[Manifest.permission.ACCESS_FINE_LOCATION]?:locationPGranted
            btPGranted = permisos[Manifest.permission.BLUETOOTH_SCAN]?:btPGranted
            btScanPGranted = permisos[Manifest.permission.BLUETOOTH]?:btScanPGranted
            btConGranted = permisos[Manifest.permission.BLUETOOTH_CONNECT]?:btConGranted
            lmac=permisos[Manifest.permission.ACCESS_WIFI_STATE]?:lmac

        }
        requestPermision()


        bleSCAN = bleAdaptador!!.bluetoothLeScanner




        //Referencias cloud firestore .-
        val docRef = db.collection("escaner").document("estado")
        val participanteRef=db.collection("participantes").document(serie!!)//Obtiene el rol del participante.
        val escConfig = db.collection("configuracion").document("general")

        leerColeccion(participanteRef)
        escConfig.addSnapshotListener{
            snap,e->
            if(e!=null){
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {

                //Log.d(TAG, "Current data: ${snap.data}")
                SCAN_PERIOD = snap.data?.get("duracionScaner").toString().toLong()*1000 //Multiplicar por 1000 para pasar a millisegundos
                FREQ = snap.data?.get("frequency").toString().toLong()*60000 //A minutos.
                nombre_experimento=snap.data?.get("nombre").toString()
                duracion_ins = snap.data?.get("duracionIns").toString().toLong()*60000//60000 millisecons son un minuto.
                //Log.d(TAG, "Parámetros de configuración: ${snap.data}")
                Log.d("PARAMETROS CONFIG", "Duracion Escaner: ${SCAN_PERIOD} y frecuencia escaner: ${FREQ} ; nombre de instancia: ${nombre_experimento}; duracion instancia: ${duracion_ins}")
            }
            else {
                Log.d(TAG, "Current data: null")
            }
        }

        //Timer de activación de escaner cada x tiempo.
        timer=object:CountDownTimer(duracion_ins,FREQ){
            override fun onTick(p0: Long) {
                scanBLE()
            }
            override fun onFinish() {
                binding.instruccion1.text = "Escaner finalizado..."
            }
        }

        //Detecta si desde la pagina web se genera un cambio en la inicialización del escaner.
        docRef.addSnapshotListener{
            snap, e->
            if (e != null){
                Log.w(TAG, "Listen failed.", e)
                return@addSnapshotListener
            }
            if (snap != null && snap.exists()) {

                Log.d(TAG, "Current data: ${snap.data}")
                if(snap.data?.get("iniciado")==false){
                    binding.instruccion1.text = "Escaner no iniciado"
                }else{
                    binding.instruccion1.text = "Escaner iniciado..."
                }

                if (snap.data?.get("iniciado")==true){
                    Log.d("mac","${serial}")
                    timer.start()
                }else{
                    timer.cancel()
                }
            } else {
                Log.d(TAG, "Current data: null")
            }
        }
    }


    private fun scanBLE(){
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                scanning = false
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_SCAN
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_SCAN),100)
                }
                bleSCAN!!.stopScan(bleScanCallback)
                Log.d("ESCANER","Deteniendo el escaneo")
            }, SCAN_PERIOD)
            scanning = true
            bleSCAN!!.startScan(null,scanConfig,bleScanCallback)
            Log.d("ESCANER","Se inicia el escaneo")
        } else {
            scanning = false
            bleSCAN!!.stopScan(bleScanCallback)
            Log.d("ESCANER","Deteniendo el escaneo")
        }
    }


    @SuppressLint("NewApi")
    private fun horaActual(): String? {
        var hora:String?=null
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        val currentdate = sdf.format(Date())
        hora = currentdate.toString()
        //Log.d("FECHA","Fecha actual: ${hora}")
        return hora
    }

    //@RequiresApi(Build.VERSION_CODES.O)
    fun getDeviceIdentifier(context: Context): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }


    private fun leerColeccion(docRef: DocumentReference){
        docRef.get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    try{
                        datos_part=document.data!!
                        binding.tvMensaje.text="Rol participante: ${datos_part?.get("Rol")}"
                    }catch (e:Exception){
                        binding.tvMensaje.text="Participante no registrado!!!"
                    }

                    //Log.d("PARTICIPANTE", "Data: ${datos_part?.get("MAC").toString()}")
                } else {
                    var mensaje="Participante no registrado!"
                    Log.d("TAG", "No such document")
                }
            }
            .addOnFailureListener { exception ->
                Log.d("TAG", "get failed with ", exception)
            }
    }
}