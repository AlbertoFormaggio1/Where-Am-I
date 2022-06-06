package com.example.progettoembedded

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

class ReaderService : LifecycleService() {

    /**
     * Binder returned when someone binds to the service. Once the service is instantiated, it remains the same until the service gets stopped
     */
    private val mBinder : IBinder = MyBinder()

    /**
     * Samples list. List of collected samples. Contains all the location retrieved in the last MINUTES minutes
     */
    var samplesList = mutableListOf<LocationDetails>()
        private set

    /**
     * Current sample. The last sample that was retrieved. Its longitude, latitude, altitude is null if no sample was collected or if the
     * location is not available.
     */
    var currentSample : LocationDetails = LocationDetails(null, null, null, Date(System.currentTimeMillis()))
        private set

    /**
     * Fused location provider. Used to collect the locations
     */
    private lateinit var fusedLocationProvider : FusedLocationProviderClient


    private var bindCount = 0

    /**
     * Is foreground. Checks whether the service is already a foreground service or not. If this variable is true, the service is a foreground service
     * and we do not have to publish another notification
     */
    private var isForeground = false

    /**
     * Is collecting location. If the location is being retrieved this variable is set to true.
     */
    var isCollectingLocation = false
        private set

    /**
     * Started. If the service has already been started, this variable is set to true. We initialize the service just once.
     */
    private var started = false

    /**
     * On create. This function is called when the service is created for the first time.
     * It creates the notificationChannel where the notification needed for starting a foreground service will be pushed
     *
     */
    override fun onCreate() {
        super.onCreate()
        Log.d("Service","Created")

        //Starting in Android 8.0 (API level 26), all notifications must be assigned to a channel.
        //Therefore we are creating a channel and adding it to the notification manager.
        //We need a notification because a notification is mandatory when launching a service as a foreground service, otherwise the service will
        //be killed by Android itself.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name : CharSequence= getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(CHANNEL_ID, name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    /**
     * On bind. Handles the binding from the MainActivity.
     *
     * @param intent
     * @return an object implementing the IBinder interface, in this case, it is a reference to ReaderService.
     */
    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        handleBind()
        return mBinder
    }

    /**
     * On rebind. Called when a component binds again to the service after the first binding already occured.
     *
     * @param intent
     */
    override fun onRebind(intent: Intent?) {
        super.onRebind(intent)
        handleBind()
    }

    /**
     * Handle bind. Handles the binding of a component to the service. Starts the service so we can handle the service lifecycle separately
     * from the one of the activity
     */
    private fun handleBind(){
        bindCount++
        //Start the service itself, this way the lifecycle of the Service will be managed by the service itself. It will be separate from the one of the mainActivity
        startService(Intent(applicationContext,this::class.java))
    }


    override fun onUnbind(intent: Intent?): Boolean {
        super.onUnbind(intent)

        Log.d("unbind","Service unbounded")

        bindCount--

        // Allow clients to rebind, in which case onRebind will be called.
        return true
    }


    /*
     * This method will be called when there is a change in the service configuration.
     * It will decide whether the service should enter foreground mode/exit foreground mode or be killed
     *
    private fun manageService(){
        when{
            isBound() -> exitForeground()

            isCollectingLocation && !mChangingConfiguration -> activateForeground()

            !mChangingConfiguration -> stopSelf()
        }

        //If the user is not sharing its location and the app is not in the foreground, it might be suspicious and misleading for
        //the user to see that their location is being retrieved.
        if(!mChangingConfiguration && !isBound() && !isCollectingLocation)
            exitForeground()
        else
            activateForeground()
    }
     */

    /**
     * Exit foreground. Removes the Service from the foreground state if it is in foreground.
     */
    private fun exitForeground(){
        if(isForeground) {
            isForeground = false
            stopForeground(true)
        }
    }

    /**
     * Activate foreground. Activates the foreground state for this service it not already in foreground.
     *
     */
    private fun activateForeground(){
        if(!isForeground){
            isForeground = true

            // Build a notification with basic info about the song
            val notificationBuilder = Notification.Builder(applicationContext, CHANNEL_ID)

            //Setting the parameters for the notification to be displayed.
            val notification = notificationBuilder.apply {
                setContentTitle(getString(R.string.notification_title))
                setContentText(getString(R.string.notification_description))
                setSmallIcon(R.drawable.icon_topbar)
            }.build()

            // Runs this service in the foreground,
            // supplying the ongoing notification to be shown to the user
            startForeground(3457689, notification)
        }
    }

    /**
     * On start command. Called when someone binds to the service for the first time. Starts to collect the location updates if not already done earlier.
     * The service becomes a foreground service
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        Log.d("Service","Started")

        if(!started) {
            //Get a provider
            fusedLocationProvider = LocationServices.getFusedLocationProviderClient(application)
            //delete all previous locations already collected for any reasons
            fusedLocationProvider.flushLocations()
            //The service is started, so we will not need to startLocationUpdates another time
            started = true
            //Starts Location updates
            startLocationUpdates()
        }

        //Manage what to do with the service.
        //As the service is START_STICKY, it could be that it is started by the system after everyone is unbounded, so we have
        //to stop the service in that case.
        //manageService()

        //The service needs to be a foreground one.
        //Although it is not necessary to have the service as a foreground service even when the app is running, there is no harm in doing so. Therefore,
        //the service becomes a foreground one as soon as possible and will be in foreground until the app is closed.
        //This results in easier acquisition of the location and also there will not be any problem related to the Service <-> Foreground Service change
        activateForeground()

        //If the service is killed by the system, tell android not to restart it
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()

        exitForeground()
        isCollectingLocation = false
        samplesList.clear()
        currentSample = LocationDetails(null, null, null, Date(System.currentTimeMillis()))
        started = false
        fusedLocationProvider.removeLocationUpdates(locationCallback)
        fusedLocationProvider.flushLocations()
    }

    /**
     * Starts to collect location updates once the needed permissions are accepted by the user.
     *
     */
    @SuppressLint("MissingPermission") //I do not know why Lint thinks I do not check permissions
    internal fun startLocationUpdates(){
        //Checks for permissions (although it is not necessary as we get here only after the activity is bound to the service, therefore when the permissions for the location
        //were already granted).
        if((ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(application, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) ||
            isCollectingLocation) {
            return
        }

        //Sets a small delay after starting the location updates. This operations is needed as the position is retrieved in the main thread as well as the map
        //for showing the position. This may generate problems due to the fact that the location retrieval could happen at slower intervals.
        //Therefore, we are setting this delay in order for the map to terminate its rendering
        lifecycleScope.launch {
            delay(1500)
            Log.d("startLocationUpdates", "Started location updates")

            fusedLocationProvider.requestLocationUpdates(
                locationRequest,
                locationCallback,
                application.mainLooper
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)

            //The app is collecting location again
            isCollectingLocation = true
            Log.d("onLocationResult","NewLocation")

            //Updating the displayed location with locationResult.lastLocation
            updateLocation(locationResult.lastLocation)
        }

        @SuppressLint("MissingPermission")
        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            super.onLocationAvailability(locationAvailability)

            //If the location is not available we send a null location, this way we can update the interface showing that No Data is available
            if(!locationAvailability.isLocationAvailable) {
                Log.d("Availability","not available")
                updateLocation(null)
            }

            isCollectingLocation = locationAvailability.isLocationAvailable
        }
    }

    /**
     * Update location. Updates the current location with the one that was just retrieved. All the observers are notified after the update
     *
     * @param location The location to update the current one with.
     */
    private fun updateLocation(location : Location?) {
        //Checking if location is null. If GPS was deactivated manually by the user at the start or the location is not retrievable, we cannot show data. We inform the user
        //that the phone location will not be available until he turns on GPS
        //Therefore, we are checking whether the location is valid or not.
            if (location != null) {
                //OnLocationResult gets always called when the app is called. I have to filter the position retrieved. It may happen that the location
                //passed to this method is due to the closure of the app instead of being the "periodical" one
                currentSample = LocationDetails(
                    location.longitude.toString(),
                    location.latitude.toString(),
                    location.altitude.toString(),
                    Date(location.time)
                )

                Log.d("updateLocation", currentSample.timestamp.seconds.toString())
                //Inserting the sample in the list
                insertSampleInList()
            }
            else
                currentSample = LocationDetails(null, null, null, Date(System.currentTimeMillis()))

        //Notifying all listeners that a new sample has been retrieved
        val intent = Intent("NewSample")
        LocalBroadcastManager.getInstance(application).sendBroadcast(intent)
    }

    /**
     * Insert the retrieved sample in the list and notifies the observers that the list has changed.
     * Furthermore, it removes all the samples that are older than MINUTES minutes.
     *
     */
    private fun insertSampleInList()
    {
        samplesList.add(currentSample)

        val threshold = Date(System.currentTimeMillis() - MINUTES * 60 * 1000)

        //Removing all the samples until all the samples are in the range from now to 5 minutes earlier.
        //I had to do it this way because iterating with for(sample in samplesList) was causing ConcurrentModificationException
        //as removing an element from the ArrayList invalidates the iterator.
        while(samplesList[0].timestamp < threshold){
            samplesList.removeAt(0)
        }

        Log.d("sender","Broadcast message")
        val intent = Intent("ListUpdated")
        LocalBroadcastManager.getInstance(application).sendBroadcast(intent)
    }



    //Using a binder just to return a reference to the Service (approach 2)
    inner class MyBinder : Binder(){
        //Returning an instance to the service. The client will be able to call public methods of the service
        fun getService() : ReaderService = this@ReaderService
    }

    //Creating a static object ==> singleton pattern
    companion object{
        //Creates a locationRequest object and sets its parameters
        val locationRequest = LocationRequest.create().apply {
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
            interval = SECONDS_INTERVAL * 1000L

            // Sets the fastest rate for active location updates. This interval is exact, and your
            // application will never receive updates more frequently than this value.
            // The maximum interval if you are using the maximum power of your device
            fastestInterval = SECONDS_INTERVAL * 1000L

            // Sets the maximum time when batched location updates are delivered. Updates may be
            // delivered sooner than this interval.
            maxWaitTime = 1000L

            //Priorities accuracy over battery usage
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY

            //Do not wait for accurate location if it is not available immediately
            isWaitForAccurateLocation = false
        }

        //Locations of the last 5 minutes will be kept by the application
        private const val MINUTES = 5

        //How many seconds between a position retrieval and the following
        const val SECONDS_INTERVAL = 5

        private const val CHANNEL_ID = "LocationReader"
    }
}