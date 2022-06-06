package com.example.progettoembedded

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel

/**
 * Class used to contain objects that we want to survive configuration changes and also objects we want to share among activities and fragments,
 *
 * @constructor Create empty Activity view model
 */
class ActivityViewModel : ViewModel() {

    /**
     * Reader service. Reader Service object, we are inserting the service inside the activityViewModel: this way we can share the service
     * between the activity and the fragments. We bind to the service just once in the activity, not in the fragments.
     * https://stackoverflow.com/questions/24309379/bind-service-to-activity-or-fragment
     */
    @SuppressLint("StaticFieldLeak")
    lateinit var readerService : ReaderService

    /**
     * Tells us whether the activity is bound to the service. We are keeping this inside the ActivityViewModel because we do not want to tie this variable
     * to the lifecycle of the activity.
     */
    var mBound = false
}