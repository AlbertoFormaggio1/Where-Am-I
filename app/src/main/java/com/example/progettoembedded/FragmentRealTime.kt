package com.example.progettoembedded

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


class FragmentRealTime : Fragment() {
    /**
     * TextView showing Longitude. We are keeping this as variable in order to prevent to ask the UI for this textView
     * everytime we want to update the UI
     */
    private lateinit var tvLong : TextView

    /**
     * TextView showing Latitude. We are keeping this as variable in order to prevent to ask the UI for this textView
     * everytime we want to update the UI
     */
    private lateinit var tvLat : TextView

    /**
     * TextView showing Altitude. We are keeping this as variable in order to prevent to ask the UI for this textView
     * everytime we want to update the UI
     */
    private lateinit var tvAlt : TextView

    /**
     * It tells if we should center the camera of the map every time a position is retrieved. If the user has moved the map, we want to keep
     * the settings made to the map by the user themselves instead of re-centering
     */
    private var moveCamera = true

    /**
     * Reference to the map shown on the screen
     */
    private lateinit var map : GoogleMap

    /**
     * ActivityViewModel shared with Activity and Fragment
     */
    private val model: ActivityViewModel by activityViewModels()

    /**
     * True if the map has already been initialized, false otherwise
     */
    private var initialized = false

    private val receiverData : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("ReceiverSample","ReceivedData")

            //If the activity is bound to the user, we update the ui
            if(model.mBound) {
                updateUI(true)
            }
        }
    }

    private fun updateUI(animate: Boolean)
    {
        val sample = model.readerService.currentSample
        //Update the 3 cards shown at the top of the screen
        updateCards(sample)
        //If latitude and longitude are valid
        if (sample.latitude != null && sample.longitude != null) {
            //if the map has been initialized
            if(initialized) {
                //Insert the marker with the current position of the user
                insertMarker()
                //Center the camera to where the marker is placed if we should do so (the user has not moved the map camera)
                if (moveCamera) {
                    moveCameraToCurrentPosition(animate)
                }
            }
        }
        else {
            //if a valid position is not available, we will not show anything on the map
            map.clear()
        }
    }

    /**
     * Inserts a marker in the current position if the app is collecting locations.
     *
     */
    private fun insertMarker(){
        if(model.mBound && model.readerService.isCollectingLocation && initialized) {

            val sample = model.readerService.currentSample
            //Latitude and longitude cannot be null if the app is collecting location. There is no need to check
            val pos = LatLng(sample.latitude!!.toDouble(), sample.longitude!!.toDouble())

            //Creating the icon object to show as marker
            var bitmap =
                AppCompatResources.getDrawable(requireContext(), R.drawable.ic_marker3)!!.toBitmap()
            bitmap = Bitmap.createScaledBitmap(bitmap, 130, 130, false)
            val icon = BitmapDescriptorFactory.fromBitmap(bitmap)

            //Creating the marker
            val marker = MarkerOptions()
                .position(pos)
                .title(getString(R.string.you_are_here))
                .snippet("lat:" + pos.latitude.toString() + ", lng:" + pos.longitude.toString())
                .icon(icon)
            //Clear the marker previously positioned
            map.clear()
            //Add the new marker to the map
            map.addMarker(marker)
        }
    }


    @SuppressLint("MissingPermission")
    private val callback = OnMapReadyCallback { googleMap ->

        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */

        map = googleMap

        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false
        /*
        gMap.uiSettings.isScrollGesturesEnabled = false
        gMap.uiSettings.isZoomGesturesEnabled = false
        */

        map.setOnCameraMoveStartedListener {
            //If the camera has been moved by the user with a gesture, we have to stop to recenter the map every time a new location
            //is available
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                Log.d("moveCamera", "Camera moving")
                moveCamera = false
            }
        }

        //insert the marker in the current position
        insertMarker()

        //The map has been initialized
        initialized = true
    }

    /**
     * When the fragments is in the foreground and receives the input from the user we subscribe for updates from the service again and
     * we update the UI if possible.
     *
     */
    override fun onResume() {
        super.onResume()

        //Using LocalBroadcastManager instead of simple BroadcastManager. This has several advantages:
        // - You know that the data you are broadcasting won't leave your app, so don't need to worry about leaking private data.
        // - It is not possible for other applications to send these broadcasts to your app, so you don't need to worry about having security holes they can exploit.
        // - It is more efficient than sending a global broadcast through the system.
        //we are subscribing for updates about the latest sample
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiverData, IntentFilter("NewSample"))

        //If the activity is bounded to the service, I can show the current element instantly without waiting for an update (better
        //for user experience).
        //Otherwise, it it is not bound it may due to 2 reasons:
        // 1. The app does not have permissions, therefore the app will not be bound to the service until these permissions are granted.
        // 2. The app has just resumed from the background, therefore the main activity has to bound to the service. If it is not bound
        //    maybe in the near future (after a few milliseconds) it could be that the activity will be bound to the service.
        // Hence, we wait a few milliseconds and if the activity is now bound we are in scenario 2 and we can update the UI. Otherwise, we are
        // in scenario 1 and we will not do anything
        if(model.mBound){
            //We want to update the UI but move the camera to current position without any animation (animate set to false)
            updateUI(false)
        }
        else{
            lifecycleScope.launch {
                delay(200)
                if(model.mBound)
                    updateUI(false)
            }
        }
    }

    /**
     * Moves the camera to the last retrieved position in the readerService
     *
     * @param animate true if the movement has to be animated, or false if we can change the map camera position drastically
     */
    private fun moveCameraToCurrentPosition(animate : Boolean){
        val sample = model.readerService.currentSample
        if(sample.latitude != null && sample.longitude != null) {
            val pos = LatLng(sample.latitude.toDouble(), sample.longitude.toDouble())
            val update = CameraUpdateFactory.newLatLngZoom(pos, 15f)
            if(animate)
                map.animateCamera(update)
            else
                map.moveCamera(update)
        }
    }

    /**
     * UnRegisters the receiver when the fragment is not visible, there is no need to keep receiving the updates if we do not need to update the
     * interface.
     */
    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiverData)
        receiverData.abortBroadcast
    }


    /**
     * Creates the view the first time the fragment is created.
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState : Bundle?): View?{
        val view = inflater.inflate(R.layout.fragment_real_time, container,false)

        //Requires the map asynchronously (operation done in the main thread)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)


        //Setting up cards programmatically
        val listLabel : List<String> = listOf(getString(R.string.label_longitude),getString(R.string.label_latitude),getString(
                    R.string.label_altitude))
        val listImg : List<Int> = listOf(R.drawable.ic_longitude,R.drawable.ic_latitude,R.drawable.ic_altitude)
        val list : List<FrameLayout> = listOf(view.findViewById(R.id.longitude),view.findViewById(R.id.latitude),view.findViewById(R.id.altitude))
        //Iterate through the frameLayouts and inserts the labels, icons, etc.
        //With a loop the initialization is smoother and easier to understand
        for ((index, e) in list.withIndex()) {
            val tvLabel = e.findViewById<TextView>(R.id.label)
            tvLabel.text = listLabel[index]

            val imgView = e.findViewById<ImageView>(R.id.imageView)
            //Requiring Drawable for better performance
            val myImage: Drawable? = ResourcesCompat.getDrawable(requireContext().resources, listImg[index], null)
            imgView.setImageDrawable(myImage)
        }

        //Gets the textViews
        tvLong = list[0].findViewById(R.id.value)
        tvLat = list[1].findViewById(R.id.value)
        tvAlt = list[2].findViewById(R.id.value)

        //Button to center the map
        val button = view.findViewById<Button>(R.id.center_button)
        button.setOnClickListener {
            if(model.mBound){
                if(initialized)
                    moveCameraToCurrentPosition(true)
            }
            else{
                Toast.makeText(requireContext(),
                    getString(R.string.last_location_missing),
                    Toast.LENGTH_LONG)
                    .show()
            }

            moveCamera = true
        }

        return view
    }


    /**
     * Update the cards showing the last positions after an update has been sent by the service.
     *
     * @param location location details to update the cards with. If any of the 3 components is null, that means that it was impossible to retrieve
     * the location. Therefore, we show that No data is available.
     */
    private fun updateCards(location : LocationDetails)
    {
        if (location.longitude != null && location.latitude != null && location.altitude != null) {
            //Keeping always 7 decimal digits. Without this conversion a number such as 7.2, would be printed as 7.2 instead of
                //7.2000000 (better to show the same number of digits for every number, it is also nicer to see by the user itself)
            tvLong.text = String.format("%.7f", location.longitude.toDouble())
            tvLat.text = String.format("%.7f", location.latitude.toDouble())
            tvAlt.text = String.format("%.7f", location.altitude.toDouble())
            Log.d("Second", location.timestamp.seconds.toString())
        } else {
            tvAlt.text = resources.getString(R.string.label_nodata)
            tvLat.text = resources.getString(R.string.label_nodata)
            tvLong.text = resources.getString(R.string.label_nodata)
            Log.d("Second", "No data")
        }
    }
}