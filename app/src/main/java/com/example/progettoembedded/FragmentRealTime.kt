package com.example.progettoembedded

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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions


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

    /**
     * Variable used to display just one toast at once.
     * When we the user clicks on the "Center" button multiple times, this would cause many toasts to queue up. Therefore,
     * we are saving the current toast and if the user generates another toast, we cancel the current toast and display a new one.
     * This way, there will always be one toast in the queue
     */
    private var toast: Toast? = null

    private val receiverData : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("ReceiverSample","ReceivedData")

            //If the activity is bound to the user, we update the ui
            if(model.mBound) {
                updateUI(true)
            }
        }
    }

    /**
     * Updates the Interface: So both the value displayed on the cards and on the map
     *
     * @param animate
     */
    private fun updateUI(animate: Boolean)
    {
        if(!model.mBound)
            return

        val sample = model.readerService!!.currentSample
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
        else if(initialized){
            //if a valid position is not available, we will not show anything on the map. The information about NoData available
            //is handled already by the function updateCards()
            map.clear()
        }
    }

    /**
     * Inserts a marker in the current position if the app is collecting locations.
     *
     */
    private fun insertMarker(){
        if(model.mBound && model.readerService!!.isCollectingLocation && initialized) {

            val sample = model.readerService!!.currentSample
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
                .snippet("Lat:" + pos.latitude.toString() + ", Lng:" + pos.longitude.toString())
                .icon(icon)
            //Clear the marker previously positioned
            map.clear()
            //Add the new marker to the map
            map.addMarker(marker)
        }
    }

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

        //Disabling zoom +/- controls at the bottom right-hand side of the screen
        map.uiSettings.isZoomControlsEnabled = false
        //The user cannot tilt the map
        map.uiSettings.isTiltGesturesEnabled = false

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

        updateUI(false)
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
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiverData, IntentFilter(ReaderService.ACTION_NEW_SAMPLE))

        //We want to update the UI but move the camera to current position without any animation (animate set to false)
        updateUI(false)
    }

    /**
     * Moves the camera to the last retrieved position in the readerService
     *
     * @param animate true if the movement has to be animated, or false if we can change the map camera position drastically
     */
    private fun moveCameraToCurrentPosition(animate : Boolean){
        val sample = model.readerService!!.currentSample
        if(sample.latitude != null && sample.longitude != null) {
            val pos = LatLng(sample.latitude.toDouble(), sample.longitude.toDouble())
            val update = CameraUpdateFactory.newLatLngZoom(pos, 15f)
            //Checking if I should move to the current position with or without animation
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
        //remove pending broadcasts, they must not be handled when the receiver is restarted
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
        //Inflating layout from the resources
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
            if(model.mBound && model.readerService!!.isCollectingLocation){
                if(initialized)
                    //If the map was initialized and the service is bound, then move to the current position
                    moveCameraToCurrentPosition(true)
            }
            else {
                //The following code prevents from many toasts to be queued when the user clicks several times on the button center
                //when the map is not ready (for example the GPS is deactivated or we are just waiting for the map to load).

                //Cancel the previous toast from the queue
                toast?.cancel()

                //Create a new toast
                toast = Toast.makeText(
                    requireContext(),
                    getString(R.string.last_location_missing),
                    Toast.LENGTH_SHORT
                )

                //Show the new toast
                toast?.show()
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
                //7.2000000 (better to show the same number of digits for every number, it is also nicer to see by the user themselves)
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