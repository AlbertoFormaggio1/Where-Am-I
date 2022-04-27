package com.example.progettoembedded

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ApplicationViewModel (application : Application) : AndroidViewModel(application) {

    //We have to create the locationLiveData object that will be visible to all the fragments
    private val locationliveData = LocationLiveData(application)
    fun getLocationLiveData() = locationliveData
    fun startLocationUpdates(){
        locationliveData.startLocationUpdates()
    }
}

class ApplicationViewModelFactory(val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>) : T {
        if (modelClass.isAssignableFrom(ApplicationViewModel::class.java)) {
            return ApplicationViewModel(application) as T
        }
        throw IllegalArgumentException("Unable to construct viewmodel")
    }
}