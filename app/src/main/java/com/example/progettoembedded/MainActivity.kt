package com.example.progettoembedded


import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigationrail.NavigationRailView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class MainActivity : AppCompatActivity() {

    /**
     * Request permission launcher. Activity result handler after permissions request
     */
    private lateinit var requestPermissionLauncher : ActivityResultLauncher<Array<String>>

    /**
     * Activity result handler responsible for checking if, after the settings page has been closed, the location was
     * activated
     */
    private lateinit var getResult : ActivityResultLauncher<Intent>

    /**
     * Set to true if the app has already asked once to activate the location in the application lifecycle
     */
    private var hasAskedForPosition = false

    /**
     * ViewModel containing shared information between fragments and this activity
     */
    lateinit var model : ActivityViewModel

    //region Handling connection to service
    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to ReaderService, cast the IBinder and get ReaderService instance
            val binder = service as ReaderService.MyBinder
            model.readerService = binder.getService()

            //The activity has bound to the service
            model.mBound = true

            checkAskPermissions()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            model.mBound = false
            model.readerService = null
        }
    }

    //endregion


    /**
     * On create. Called when the activity is created for the first time (or after being destroyed due to a configuration change)
     *
     * @param savedInstanceState
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        //Disabling Dark mode
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)

        //Initializing ActivityViewModel
        val myModel : ActivityViewModel by viewModels()
        model = myModel

        setContentView(R.layout.activity_main)

        //After a configuration change, we want to know if we already asked the user to activate the location.
        if(savedInstanceState != null)
            hasAskedForPosition = savedInstanceState.getBoolean("hasAskedForLocation")

        //What to do after the user leaves the settings page after opening it from the dialog asking him to enable the location
        getResult = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()) {
            //Check whether the GPS is enabled or not without showing the related dialog
            checkGPSEnabled(false)
        }

        //Setting up Bottom navigation bar programmatically based on the orientation.
        //If the phone is PORTRAIT mode, then we are using a bottomNav to show the menu, otherwise we will be using a NavigationRailView
        //as sidebar
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        val navController = navHostFragment.navController
        val myNav = findViewById<View>(R.id.bottomNav)
        if(this.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            val bottomNav = myNav as BottomNavigationView
            NavigationUI.setupWithNavController(bottomNav,navController)
        }
        else{
            val sideNav = myNav as NavigationRailView
            NavigationUI.setupWithNavController(sideNav,navController)
        }

        //The name displayed on the appBar needs to be the name of the current fragment
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.fragmentRealTime, R.id.fragmentList))
        setupActionBarWithNavController(navController, appBarConfiguration)
    }

    /**
     * On start. Initialize what to do after the request to the user to give the app the permissions. Called when the app exits from the background.
     *
     */
    override fun onStart() {
        super.onStart()

        //Managing permissions and how to handle responses to permissions request.
        //Checking this every time the app is started because it could be that the user disabled the location manually after closing the app
        initializeResultLauncher()

        //Binding to Service
        val intent = Intent(this, ReaderService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * When the app leaves the foreground, we are unbinding the activity from the service
     *
     */
    override fun onStop() {
        super.onStop()

        if(model.mBound){
            unbindService(connection)
        }
    }

    /**
     * Initializes the {@code ActivityResultCallback} responsible for handling the user's response to the permission request
     */
    private fun initializeResultLauncher()
    {
        requestPermissionLauncher = registerForActivityResult( ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                //If at least one of the first 2 permission is available, then readerService is initialized
                (permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) ||
                        permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false)) -> {
                    requestLocationUpdates()
                }
                else -> {
                    Toast.makeText(
                        applicationContext,
                        R.string.permission_missing,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * When a configuration change occurs, we are saving in the instance state if we already asked the user to enable to position.
     *
     * @param outState
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //If the position has already been asked to the user in this instance of the app, then we will not be asking the position again
        //even when the screen is rotated (hence, the activity gets destroyed).
        //If the app is restarted, we want to ask the user to enable the position if not already enabled
        outState.putBoolean("hasAskedForLocation",hasAskedForPosition)
    }

    /**
     * Check GPS enabled. Checks if the location has been enabled and show the user a dialog asking him to enable the GPS on its phone if
     * requested.
     *
     * @param shouldShowDialog If this method should show a dialog or not
     */
    private fun checkGPSEnabled(shouldShowDialog : Boolean){
        //Creating a dummy request, I just need to set the priority of the requests that will be done in the future.
        val dummyRequest = LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        val builder = LocationSettingsRequest.Builder().addLocationRequest(dummyRequest)

        //We are using SettingsClient (from Google Play Services) to check if the position was enabled.
        //https://developers.google.com/android/reference/com/google/android/gms/location/SettingsClient
        //This could have been done more easily by using LocationManager, however, for consistency reasons i.e. using the same
        //library without using 2 Location Providers, I decided to implement this by using Google's way.
        val result = LocationServices.getSettingsClient(this).checkLocationSettings(builder.build())

        //After checkLocationSettings has finished, this listener will be called
        result.addOnCompleteListener {
            try {
                //Get result doesn't throw exception only if the settings are already enabled
                it.getResult(ApiException::class.java)
            }
            catch(exception : ApiException){
                //We show the dialog only if shouldShowDialog is set to true, otherwise we will be just showing the user a toast asking to
                    // enable gps
                if(shouldShowDialog) {
                    when (exception.statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> buildAlertMessageNoGps()
                        LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> Log.d(
                            "Settings_Change_Unavailable",
                            "Unlikely"
                        )
                    }
                }
                else{
                    Toast.makeText(this, resources.getString(R.string.enable_GPS), Toast.LENGTH_SHORT).show()
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
        val arrayPermissions = arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION)

        //First of all, we check if the gps is enabled.
        if(!hasAskedForPosition) {
            hasAskedForPosition = true
            checkGPSEnabled(true)
        }

        //Then, we check if the permissions were already granted. If they were not and the app should show RequestPermissionRationale, we
        //show the user a dialog, otherwise we just ask the user the location.
        when {
            //Permissions already granted
            (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)-> {
                requestLocationUpdates()
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> {
                val title = resources.getString(R.string.location_permission_title)
                val message = resources.getString(R.string.location_permission_message)
                        buildAlertPermissions(title,message,arrayPermissions)
                    }
            else -> askPermissions(arrayPermissions)
        }
    }

    /**
     * Request location updates. Binds to the service (the binding also involves the start of the location updates)
     *
     */
    private fun requestLocationUpdates() {
        //Sets a small delay after starting the location updates. This operations is needed as the position is retrieved in the main UI thread as well as the map
        //for showing the position. This may generate problems due to the fact that the location retrieval can be delayed as the map rendering is being done in the mainLooper..
        //Therefore, we are setting this delay in order for the map to terminate its rendering before starting the location updates.
        if(model.mBound){
            lifecycleScope.launch {
                delay(2000)
                model.readerService!!.startLocationUpdates()
            }
        }
    }

    /*
    private fun checkAskBackground() {
        //Background location is treated in the same way as foreground location for android versions lower than Android 11
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R)
            return

        val permissionsArray = arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        when {
            //Permissions already granted
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                model.readerService.isCollectingBackgroundLocation = true
            }
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)-> {
                val label = application.packageManager.backgroundPermissionOptionLabel
                val message = String.format(resources.getString(R.string.background_permission_message),label)
                val title = resources.getString(R.string.background_permission_title)
                buildAlertPermissions(title, message, permissionsArray)
            }
        }
    }*/

    /**
     * Asks permissions passed as parameters
     *
     * @param permissions permissions to ask to the user
     */
    private fun askPermissions(permissions : Array<String>)
    {
        requestPermissionLauncher.launch(permissions)
    }

    /**
     * Build alert permissions. Builds and shows the user an alertDialog before actually asking the needed permissions.
     *
     * @param title title of the dialog
     * @param message message to show to the user explaining why we need those permissions
     * @param permissions the permissions to ask if the user clicks on Yes
     */
    private fun buildAlertPermissions(title: String, message : String, permissions : Array<String>)
    {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton(getString(R.string.positive_answer)) { _, _ ->
            askPermissions(
                permissions
            )
        }

        builder.setNeutralButton(getString(R.string.negative_answer)) { _, _ ->
            Toast.makeText(
                applicationContext,
                getString(R.string.permission_missing),
                Toast.LENGTH_SHORT
            ).show()
        }
        builder.show()
    }

    /**
     * Build alert an alert dialog asking the user to enable the GPS from the settings.
     *
     */
    private fun buildAlertMessageNoGps() {
        val builder : AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setMessage(R.string.enable_GPS_message)
            .setCancelable(false)
            .setPositiveButton(getString(R.string.positive_answer))
            { _, _ ->
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                getResult.launch(intent)
            }
            .setNegativeButton(getString(R.string.negative_answer))
            { dialog, _ -> dialog.cancel()
                Toast.makeText(this, R.string.enable_GPS, Toast.LENGTH_SHORT).show()}
        val alert = builder.create()
        alert.show()
    }
}