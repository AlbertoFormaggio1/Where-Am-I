package com.example.progettoembedded


import android.Manifest
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.text.DecimalFormat
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationProvider: FusedLocationProviderClient
    // LocationRequest - Requirements for the location updates, i.e., how often you
    // should receive updates, the priority, etc.
    private lateinit var locationRequest: LocationRequest
    // LocationCallback - Called when FusedLocationProviderClient has a new Location.
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null
    private lateinit var tv_long : TextView
    private lateinit var tv_lat : TextView
    private lateinit var tv_alt : TextView
    private lateinit var requestPermissionLauncher : ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_long = findViewById(R.id.tv_longitude_data)
        tv_lat = findViewById(R.id.tv_latitude_data)
        tv_alt = findViewById(R.id.tv_altitude_data)

        fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)

        // TODO: Step 1.3, Create a LocationRequest.

        //Creates a locationRequest and sets its parameters
        locationRequest = LocationRequest.create().apply {
            // Sets the desired interval for active location updates. This interval is inexact. You
            // may not receive updates at all if no location sources are available, or you may
            // receive them less frequently than requested. You may also receive updates more
            // frequently than requested if other applications are requesting location at a more
            // frequent interval.
            //
            // IMPORTANT NOTE: Apps running on Android 8.0 and higher devices (regardless of
            // targetSdkVersion) may receive updates less frequently than this interval when the app
            // is no longer in the foreground.
            //This is the value which is usually used
            interval = TimeUnit.SECONDS.toMillis(1)

            // Sets the fastest rate for active location updates. This interval is exact, and your
            // application will never receive updates more frequently than this value.
            // The maximum interval if you are using the maximum power of your device
            fastestInterval = TimeUnit.SECONDS.toMillis(1)

            // Sets the maximum time when batched location updates are delivered. Updates may be
            // delivered sooner than this interval.
            maxWaitTime = TimeUnit.SECONDS.toMillis(1)

            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // TODO: Step 1.4, Initialize the LocationCallback.
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)

                // Normally, you want to save a new location to a database. We are simplifying
                // things a bit and just saving it as a local variable, as we only need it again
                // if a Notification is created (when the user navigates away from app).
                updateUI(locationResult.lastLocation)

                // Notify our Activity that a new location was added. Again, if this was a
                // production app, the Activity would be listening for changes to a database
                // with new locations, but we are simplifying things a bit to focus on just
                // learning the location side of things.
                //val intent = Intent("$PACKAGE_NAME.action.FOREGROUND_ONLY_LOCATION_BROADCAST")
                //intent.putExtra(EXTRA_LOCATION, currentLocation)
                //LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

                // Updates notification content if this service is running as a foreground
                // service.
                /*if (serviceRunningInForeground) {
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        generateNotification(currentLocation))
                }*/
            }
        }

        initializeResultLauncher()
        checkAskPermissions()

    }

    fun updateGPS()
    {
        //Checking if the app has the permission for using the position data
        if(ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
            fusedLocationProvider = LocationServices.getFusedLocationProviderClient(this)
            fusedLocationProvider.lastLocation.addOnSuccessListener { location ->
                updateUI(location)
            }

            // TODO: Step 1.5, Subscribe to location changes.
            fusedLocationProvider.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    /**
     * Initializes the {@code ActiviyResultCallback} responsible for handling the user's reponse to the permission request
     */
    fun initializeResultLauncher()
    {
        requestPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    updateGPS()
                }
                else -> {
                    Toast.makeText(
                        applicationContext,
                        "This app won't work without location permissions",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Checks whether the app already has the location permissions.
     * If the app already has the permission (either fine or coarse), then the position is retrieved and it is shown to the user
     * Otherwise, asks the user for permissions.
     */
    fun checkAskPermissions()
    {
        when {
            //Permissions already granted
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                updateGPS()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                        showAlertOkNo("This app requires location permissions in order to work")
                    }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                askLocationPermissions()
            }
        }
    }

    fun askLocationPermissions()
    {
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    fun showAlertOkNo(message : String)
    {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Location Permissions")
        builder.setMessage(message)
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialogInterface, i -> askLocationPermissions() })

        builder.setNeutralButton("No thanks", DialogInterface.OnClickListener{ dialogInterface, i ->
            Toast.makeText(
                applicationContext,
                "This app won't work without location permissions",
                Toast.LENGTH_LONG
            ).show()})


        builder.show()
    }


    fun updateUI(location : Location)
    {
        val df = DecimalFormat("#.########")

        tv_long.text = df.format(location.longitude).toString()
        tv_lat.text = df.format(location.latitude).toString()

        if(location.hasAltitude())
            tv_alt.text = df.format(location.altitude).toString()
        else
            tv_alt.text = "Not available"
    }
}