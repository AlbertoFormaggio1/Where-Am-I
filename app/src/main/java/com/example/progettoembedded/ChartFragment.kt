package com.example.progettoembedded

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt


class ChartFragment : Fragment() {

    /**
     * Reference to the displayed map
     */
    private lateinit var map : GoogleMap

    /**
     * Reference to the altitude chart shown at the bottom of the UI
     */
    private lateinit var altitudeChart : LineChart

    /**
     * Timestamp of the first sample
     */
    private var refTimestamp = 0L

    /**
     * Polyline options for showing the line. We keep it as a variable so we will not need to set the options (color, thickness, etc)
     * every time we draw the polyline
     */
    private lateinit var polylineOptions : PolylineOptions
    private val model: ActivityViewModel by activityViewModels()

    /**
     * Camera Update always available to center the view showing the whole path done by the user in the last 5 minutes.
     */
    private lateinit var cu : CameraUpdate

    /**
     * True if the map has already been initialized, false otherwise
     */
    private var initialized = false

    /**
     * It tells if we should center the camera of the map every time a position is retrieved. If the user has moved the map, we want to keep
     * the settings made to the map by the user themselves instead of re-centering
     */
    private var moveCamera = true

    private var toast : Toast? = null

    /**
     * Receiver data, handles what to do when the list gets updated
     */
    private val receiverData : BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("ReceiverList", "ReceivedData")

            //If the notification is received before the application is bounded to the service, ignore the update. Will be handled when
            //the bound to the service has been accomplished.
            if(model.mBound) {
                val sample = model.readerService!!.currentSample
                //refresh the altitude chart with the new list
                refreshChart(model.readerService!!.samplesList)
                //If the map is initialized insert the marker and draw the polyline
                if(initialized) {
                    map.clear()
                    //It location is not available, keep everything still until the next update
                    if (sample.latitude != null && sample.longitude != null && sample.altitude != null)
                        insertMarker()
                    drawPolyline(model.readerService!!.samplesList, moveCamera)
                }
            }
        }
    }

    /**
     * Insert the marker in the last known position.
     *
     * @param loc
     */
    private fun insertMarker() {
        val sample: LocationDetails

        //If the list is not empty, then get the sample
        //We are getting the sample this way and not with currentSample because we want to display the marker even
        //when position is not available (we want to show where the last location was collected, otherwise by just showing a line
        //the user cannot know which is the starting point and which is the ending one)
        if(model.mBound && model.readerService!!.samplesList.isNotEmpty())
            sample = model.readerService!!.samplesList[model.readerService!!.samplesList.size-1]
        else
            return

        //lastKnownLocation = sample
        //Latitude and longitude cannot be null
        if(sample.latitude != null && sample.longitude != null) {
            val pos = LatLng(sample.latitude.toDouble(), sample.longitude.toDouble())

            if (initialized) {
                var bitmap =
                    AppCompatResources.getDrawable(requireContext(), R.drawable.ic_marker3)!!
                        .toBitmap()
                bitmap = Bitmap.createScaledBitmap(bitmap, 70, 70, false)
                val icon = BitmapDescriptorFactory.fromBitmap(bitmap)
                val marker = MarkerOptions()
                    .position(pos)
                    .title(getString(R.string.you_are_here))
                    .snippet("Lat:" + pos.latitude.toString() + ", Lng:" + pos.longitude.toString())
                    .icon(icon)
                map.addMarker(marker)
            }
        }
    }

    private val callback = OnMapReadyCallback { googleMap ->
        map = googleMap
        /**
         * Manipulates the map once available.
         * This callback is triggered when the map is ready to be used.
         * This is where we can add markers or lines, add listeners or move the camera.
         * In this case, we just add a marker near Sydney, Australia.
         * If Google Play services is not installed on the device, the user will be prompted to
         * install it inside the SupportMapFragment. This method will only be triggered once the
         * user has installed Google Play services and returned to the app.
         */

        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isTiltGesturesEnabled = false

        //Create a new polylineOptions object and set all the due settings
        polylineOptions = PolylineOptions()
        polylineOptions.color(ContextCompat.getColor(requireContext(), R.color.secondary_300))
        polylineOptions.width(8f)
        //The joint between two lines is rounded. Same with first and last ends of the line.
        polylineOptions.jointType(JointType.ROUND)
        polylineOptions.startCap(RoundCap())
        polylineOptions.endCap(RoundCap())

        map.setOnCameraMoveStartedListener {
            //The user moved the camera. We stop the automatic re-centering of the map
            if (it == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                Log.d("moveCamera", "Camera moving")
                moveCamera = false
            }
        }

        initialized = true

        //Drawing the polyline/marker if possible
        fillCharts()
    }

    /**
     * Fill the carts once the fragment gets into the foreground if the activity is bounded to the service.
     *
     */
    override fun onResume() {
        super.onResume()

        //If the activity is bounded to the service, I can show the current element instantly without waiting for an update (better
        //for user experience).
        //Otherwise, it it is not bound it may due to 2 reasons:
        // 1. The app does not have permissions, therefore the app will not be bound to the service until these permissions are granted.
        // 2. The app has just resumed from the background, therefore the main activity has to bound to the service. If it is not bound
        //    maybe in the near future (after a few milliseconds) it could be that the activity will be bound to the service.
        // Hence, we wait a few milliseconds and if the activity is now bound we are in scenario 2 and we can update the UI. Otherwise, we are
        // in scenario 1 and we will not do anything
        if(model.mBound)
            fillCharts()
        else{
            lifecycleScope.launch {
                delay(200)
                fillCharts()
            }
        }

        //Subscribe to new updates.
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(receiverData, IntentFilter(ReaderService.ACTION_LIST_UPDATED))
    }

    /**
     * Fill tha map if initialized and initialize also the altitude chart with the data available.
     *
     */
    private fun fillCharts()
    {
        if(!model.mBound)
            return

        val list = model.readerService!!.samplesList
        //Refresh the altitude chart
        refreshChart(list)
        //If the map was already initialized
        if(initialized) {
            //Clear everything from the map
            map.clear()
            //Draw the polyline
            drawPolyline(list, true)
            //Insert the marker in the last known position
            insertMarker()
        }
    }

    /**
     * Unsubscribes the fragment from receiving the updates when it leaves the foreground.
     *
     */
    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(receiverData)
        receiverData.abortBroadcast
    }

    /**
     * On create view. Called the first time the fragment is created (or after a configuration change)
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState : Bundle?): View?{
        val view = inflater.inflate(R.layout.fragment_chart, container,false)

        //Initializing the center. When it is clicked we have to recenter the map in order to show all the path.
        val centerBtn = view.findViewById<Button>(R.id.center_button)
        centerBtn.setOnClickListener{
            if(model.mBound && model.readerService!!.isCollectingLocation){
                if(initialized)
                    map.moveCamera(cu)
            }
            else{
                //The following code prevents from many toasts to be queued when the user clicks several times on the button center
                //when the map is not ready (for example the GPS is deactivated or the app does not have the permissions.

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
     * On view created
     *
     * @param view
     * @param savedInstanceState
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        //initialize the chart
        initializeChart()
        //requiring the map asynchronously
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(callback)
    }

    /**
     * Draws the polyline with all the points inside the given list of samples.
     *
     * @param samples List of samples to show on the map
     * @param move true if we have to recenter the map in order to contain all the positions or leave it as it is (position modified by
     * the user themselves)
     */
    private fun drawPolyline(samples: List<LocationDetails>, move : Boolean) {
        //if there is no point to show, then return.
        if(samples.isEmpty())
            return
        //Creating a LatLngBounds Builder to insert the positions.
        //This was done in order to move the camera in the right position using the right zoom.
        //(nearer points need the map to be more zoomed in)
        val builder: LatLngBounds.Builder = LatLngBounds.Builder()
        //Clear the previously inserted points
        polylineOptions.points.clear()
        for (sample in samples) {
            //Self-asserted is safe as the list will contain only positions with valid latitude and longitude
            val pos = LatLng(sample.latitude!!.toDouble(),sample.longitude!!.toDouble())
            //Adding value to line
            polylineOptions.add(pos)
            //Adding value to builder
            builder.include(pos)
        }


        //Building a LatLngBounds object
        val bounds = builder.build()
        // offset from edges of the map in pixels
        val offset = 300
        cu = CameraUpdateFactory.newLatLngBounds(bounds, offset)
        //If the user moved the app, do not recenter the camera but leave the camera settings set by the user by using the gestures.
        //The settings of the central position are still saved in case the user wants to center the map
        if(move) {
            map.moveCamera(cu)
        }

        //Add the polyline to the map
        map.addPolyline(polylineOptions)
    }

    /**
     * Initialize the altitude chart setting all the needed settings.
     *
     */
    private fun initializeChart(){
        altitudeChart = requireView().findViewById(R.id.altitude_chart)
        //altitudeChart.setOnChartValueSelectedListener(requireActivity());

        // enable description text
        altitudeChart.description.isEnabled = false

        // enable touch gestures
        altitudeChart.setTouchEnabled(true)

        // enable scaling and dragging
        altitudeChart.isDragEnabled = false
        altitudeChart.setScaleEnabled(false)

        // if disabled, scaling can be done on x- and y-axis separately
        altitudeChart.setPinchZoom(false)

        //Setting the message to show in the text when no data is available
        altitudeChart.setNoDataText("Waiting for your location to be retrieved...")

        val data = LineData()

        // add empty data
        altitudeChart.data = data

        // get the legend (only possible after setting data)
        val l = altitudeChart.legend

        // modify the legend ...
        l.form = Legend.LegendForm.LINE
        //l.typeface = tfLight
        l.textColor = ContextCompat.getColor(requireContext(), R.color.default_text_color)

        val xl = altitudeChart.xAxis
        //xl.typeface = tfLight
        xl.position = XAxis.XAxisPosition.BOTTOM
        xl.textColor = ContextCompat.getColor(requireContext(), R.color.default_text_color)
        //Using a custom formatter for the labels over the XAxis. (we wanted to format the labels as follows: MINUTE:SECOND)
        xl.valueFormatter = MyXAxisValueFormatter()
        //Show the gridLines corresponding to each label
        xl.setDrawGridLines(true)
        xl.setAvoidFirstLastClipping(true)
        //The xAxis is enabled
        xl.isEnabled = true
        //We need to show the labels
        xl.setDrawLabels(true)
        //at least SECONDS_INTERVAL second between each label over the X axis
        xl.granularity = ReaderService.SECONDS_INTERVAL.toFloat() * 1000
        //Enabling the granularity as just set
        xl.isGranularityEnabled = true

        val leftAxis = altitudeChart.axisLeft
        leftAxis.textColor = ContextCompat.getColor(requireContext(), R.color.default_text_color)
        //Set a line every 5 meters on the y axis
        leftAxis.setDrawGridLines(true)
        //Showing a label every 5meters
        leftAxis.granularity = 5f
        leftAxis.isGranularityEnabled = true

        //Disabling right axis
        val rightAxis = altitudeChart.axisRight
        rightAxis.setDrawGridLines(false)
        rightAxis.setDrawLabels(false)
        rightAxis.setDrawAxisLine(true)

        var set = data.getDataSetByIndex(0)
        if(set == null){
            set = createSet()
            data.addDataSet(set)
        }
    }

    /**
     * Refresh the altitude chart with the new data
     *
     * @param samples
     */
    private fun refreshChart(samples : List<LocationDetails>)
    {
        //If no sample is available, then return without doing anything
        if(samples.isEmpty())
            return

        //The timeStamp used as reference
        refTimestamp = samples[0].timestamp.time
        val data : LineData? = altitudeChart.data
        //Get the max and min altitude
        var max = samples.maxOf { it.altitude!!.toFloat() }
        var min = samples.minOf { it.altitude!!.toFloat() }
        //Round the maximum and minimum to the nearest multiple of 5
        max = (max/5).roundToInt() * 5f
        min = (min/5).roundToInt() * 5f
        //Set the max as the just obtained maximum + 10m
        altitudeChart.axisLeft.axisMaximum = (max + 10f)
        //Set the max as the just obtained minimum - 10m
        altitudeChart.axisLeft.axisMinimum = (min - 10f)

        //If data is not null
        if(data != null)
        {
            //Use my custom formatter to show data
            altitudeChart.xAxis.valueFormatter = MyXAxisValueFormatter()
            val dataSet = data.getDataSetByIndex(0) as LineDataSet

            val entries = mutableListOf<Entry>()
            for(s in samples){
                //Null LocationDetails will not be pushed in the list, so altitude.toFloat() is safe
                entries.add(Entry((s.timestamp.time - refTimestamp).toFloat(), s.altitude!!.toFloat()))
            }

            //If there are too many samples, do not show circles anymore
            if(samples.size > MAX_CHART_SAMPLES)
                dataSet.setDrawCircles(false)

            dataSet.values = entries

            data.notifyDataChanged() // NOTIFIES THE DATA OBJECT

            altitudeChart.notifyDataSetChanged() // let the chart know its data changed
            altitudeChart.invalidate() // refresh
        }
    }

    /**
     * Creates a new DataSet and sets all the needed parameters.
     *
     * @return set
     */
    private fun createSet() : LineDataSet{
        refTimestamp = 20L
        val lineValues = ArrayList<Entry>()

        //Title of the dataset
        val set = LineDataSet(lineValues,"Altitude [m]")
        set.axisDependency = AxisDependency.LEFT
        //Setting color
        set.color = ContextCompat.getColor(requireContext(), R.color.secondary_500)
        //Setting circles color
        set.setCircleColor(ContextCompat.getColor(requireContext(), R.color.secondary_300))
        //Setting lineWidth
        set.lineWidth = 2f
        //Setting the radius of the circles corresponding to each sample
        set.circleRadius = 4f
        //Transparency for the area below the chart
        set.fillAlpha = 65
        set.setDrawFilled(true)
        set.fillColor = ContextCompat.getColor(requireContext(), R.color.secondary_500)
        set.highLightColor = Color.rgb(30, 30, 30)
        set.valueTextColor = Color.WHITE
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    /**
     * My x axis value formatter used for the MPChart chart in order to format the labels as desired
     *
     * @constructor Create empty My x axis value formatter
     */
    private inner class MyXAxisValueFormatter : ValueFormatter() {
        /**
         * Called when a value from an axis is to be formatted before being drawn.
         * Here value is the value in milliseconds of the value to be drawn.
         * We want the labels to be formatted as MINUTE:SECOND
         *
         * @param value the value to be formatted
         * @param axis  the axis the value belongs to
         * @return text string corresponding to the given value
         */
        override fun getAxisLabel(value: Float, axis: AxisBase?): String {
            var label = ""
            if (refTimestamp != 0L) {

                // evaluating timestamp of the current element
                val timestamp = refTimestamp + value.toLong()
                //Creating a calendar
                val calendar = Calendar.getInstance()
                //Initializing the calendar with the timestamp: Minutes and Seconds are evaluated automatically by the Date class
                calendar.time = Date(timestamp)
                label =
                    "%02d:%02d".format(calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND))
            }
            return label
        }
    }

    companion object{
        //Maximum number of samples after which we are going to hide all the circles
        private const val MAX_CHART_SAMPLES = 20
    }
}