package com.example.progettoembedded

import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer

class FragmentRealTime : Fragment() {
    private lateinit var tv_long : TextView
    private lateinit var tv_lat : TextView
    private lateinit var tv_alt : TextView
    private val applicationViewModel : ApplicationViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState : Bundle?): View?{
        val view = inflater.inflate(R.layout.fragment_real_time, container,false)

        //Setting up cards programmatically
        val listLabel : List<String> = listOf("Longitude","Latitude","Altitude")
        val listImg : List<Int> = listOf(R.drawable.ic_longitude,R.drawable.ic_latitude,R.drawable.ic_altitude)
        val list : List<FrameLayout> = listOf(view.findViewById(R.id.longitude),view.findViewById(R.id.latitude),view.findViewById(R.id.altitude))
        for ((index, e) in list.withIndex()) {
            val tvLabel = e.findViewById<TextView>(R.id.label)
            tvLabel.text = listLabel[index]

            val imgView = e.findViewById<ImageView>(R.id.imageView)
            //Requiring Drawable for better performance
            val myImage: Drawable? = ResourcesCompat.getDrawable(requireContext().resources, listImg[index], null)
            imgView.setImageDrawable(myImage)
        }

        tv_long = list[0].findViewById(R.id.value)
        tv_lat = list[1].findViewById(R.id.value)
        tv_alt = list[2].findViewById(R.id.value)

        return view
    }

    /**
     * On view created. Gets the elements in the UI.
     *
     * Meglio fare cose in onViewCreated che in onCreateView perchè nel primo caso potrebbero esserci dei crash per qualche motivo.
     * Robe legate alla view falle sempre qui, perchè qui sono sicuro che la view è già stata creata
     *
     * @param view
     * @param savedInstanceState
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applicationViewModel.getLocationLiveData().observe(viewLifecycleOwner, Observer{
            updateUI(it)
        })
    }

    private fun updateUI(location : LocationDetails)
    {
        tv_long.text = String.format("%.7f", location.longitude.toDouble())
        tv_lat.text = String.format("%.7f", location.latitude.toDouble())
        tv_alt.text = String.format("%.7f", location.altitude.toDouble())
    }
}