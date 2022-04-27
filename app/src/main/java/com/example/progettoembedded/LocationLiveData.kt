package com.example.progettoembedded

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.lifecycle.LiveData
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import java.util.*
import java.util.concurrent.TimeUnit


class LocationLiveData(var context: Context) : LiveData<LocationDetails>() {
    //We are using here the context, that's why we need context in the constructor
    private var fusedLocationProvider = LocationServices.getFusedLocationProviderClient(context)

    /*
    Called when we have at least one observer looking at this element
     */
    override fun onActive() {
        super.onActive()
        //If the app has not the permission to access to the location data, then return.
        //We cannot ask for permissions here
        startLocationUpdates()
    }

    internal fun startLocationUpdates(){
        if(ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationProvider.lastLocation.addOnSuccessListener {
                location -> location.also {
            updateLocation(location)
        }
        }

        fusedLocationProvider.requestLocationUpdates(locationRequest, locationCallback, context.mainLooper)
    }

    private fun updateLocation(location : Location?) {
        //We are modifying a data class which notifies the observers when there is a change in its values
        //The data that changes and can be stored in an attribute called value.
        //Every time we are getting a new location, we are invoking this function, creating a new LocationDetails object and we are assigning this to value
        //which is an attribute of the super class of LiveData, then all the observers which are observing value for changes will be notified that
        //value has changed and they can react to that event.

        //Checking if location is null. If GPS was deactivated manually by the user at the start, we cannot show data. We inform the user
        //that the phone location will not be available until he turns on Position Data
        if(location != null)
            value = LocationDetails(location.longitude.toString(), location.latitude.toString(), location.altitude.toString(), Date(location.time))
    }

    /**
     * Called if 0 observers are looking at this data
     *
     */
    override fun onInactive() {
        super.onInactive()
        //LocationProvider is not calling the callback anymore since no one is interested in changes in the device location
        fusedLocationProvider.removeLocationUpdates(locationCallback)
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            //Return if locationResult is null
            locationResult ?: return

            //Updating the displayed location with locationResult.lastLocation
            updateLocation(locationResult.lastLocation)
        }
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
                interval = TimeUnit.SECONDS.toMillis(1)

                // Sets the fastest rate for active location updates. This interval is exact, and your
                // application will never receive updates more frequently than this value.
                // The maximum interval if you are using the maximum power of your device
                fastestInterval = TimeUnit.SECONDS.toMillis(1)

                // Sets the maximum time when batched location updates are delivered. Updates may be
                // delivered sooner than this interval.
                maxWaitTime = TimeUnit.SECONDS.toMillis(1)

                //Priorities accuracy over battery usage
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
    }
}