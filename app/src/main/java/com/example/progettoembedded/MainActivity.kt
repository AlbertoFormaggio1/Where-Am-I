package com.example.progettoembedded


import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView


class MainActivity : AppCompatActivity() {

    lateinit var applicationViewModel : ApplicationViewModel
    private lateinit var requestPermissionLauncher : ActivityResultLauncher<Array<String>>
    private lateinit var getResult : ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        getResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            if(!isLocationEnabled()){
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()
            }
        }

        //Setting up Bottom navigation bar
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        val navController = navHostFragment.navController
        findViewById<BottomNavigationView>(R.id.bottomNav).setupWithNavController(navController)

        val appBarConfiguration = AppBarConfiguration(setOf(R.id.fragmentRealTime,R.id.fragmentList))
        setupActionBarWithNavController(navController, appBarConfiguration)

        //Setting up the applicationViewModel for communicating between fragments
        val viewModelFactory = ApplicationViewModelFactory(application)
        applicationViewModel = ViewModelProvider(this, viewModelFactory).get(ApplicationViewModel::class.java)

        //Managing permissions and how to handle responses to permissions request
        initializeResultLauncher()
        checkAskPermissions()
    }

    private fun isLocationEnabled() : Boolean
    {
        //If SDK is greater than API 28
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        {
            val lm : LocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager;
            return lm.isLocationEnabled
        }
        else
        {
            //If older API, then use deprecated version
            val mode: Int = Settings.Secure.getInt(
                this.contentResolver, Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            return mode != Settings.Secure.LOCATION_MODE_OFF
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
                    requestLocationUpdates()
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
    private fun checkAskPermissions()
    {
        if(!isLocationEnabled()) {
            buildAlertMessageNoGps()
        }

        when {
            //Permissions already granted
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                requestLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                        buildAlertPermissions("This app requires location permissions in order to work")
                    }
            else -> {
                // You can directly ask for the permission.
                // The registered ActivityResultCallback gets the result of this request.
                askLocationPermissions()
            }
        }
    }

    /**
     * Request location updates
     *
     */
    private fun requestLocationUpdates() {
        applicationViewModel.startLocationUpdates()
    }

    /**
     * Ask location permissions
     *
     */
    fun askLocationPermissions()
    {
        requestPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /**
     * Show alert ok no
     *
     * @param message
     */
    private fun buildAlertPermissions(message : String)
    {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Location Permissions")
        builder.setMessage(message)
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialogInterface, i -> askLocationPermissions() })

        builder.setNeutralButton("No thanks", DialogInterface.OnClickListener{ dialogInterface, i ->
            Toast.makeText(
                applicationContext,
                "This app won't work without location permissions",
                Toast.LENGTH_SHORT
            ).show()})


        builder.show()
    }

    private fun buildAlertMessageNoGps() {
        val builder : AlertDialog.Builder = AlertDialog.Builder(this);
        builder.setMessage("Your GPS seems to be disabled.\nThis app requires it in order to work.\nDo you want to enable GPS?")
            .setCancelable(false)
            .setPositiveButton("Yes")
            { dialog, int ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                getResult.launch(intent)
            }
            .setNegativeButton("No")
            { dialog, id -> dialog.cancel()
                Toast.makeText(this, "Please enable GPS", Toast.LENGTH_SHORT).show()};
        val alert = builder.create()
        alert.show()
    }
}