package com.example.progettoembedded

import android.content.Context
import androidx.lifecycle.LiveData

class LocationLiveData(var context: Context) : LiveData<LocationDetails>() {

    /*
    Called when we have at least one observer looking at this element
     */
    override fun onActive()
    {
        super.onActive()
    }

    /*
    Called when there are 0 observers
     */
    override fun onInactive()
    {
        super.onInactive()
    }
}