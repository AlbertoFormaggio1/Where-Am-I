package com.example.progettoembedded

import android.annotation.SuppressLint
import androidx.lifecycle.ViewModel

/**
 * Class used to contain objects that we want to survive configuration changes and also objects we want to share among activities and fragments.
 *
 * @constructor Create empty Activity view model
 */
class ActivityViewModel : ViewModel() {

    /*The reason why the activityViewModel was used is the following:
        The class ActivityViewModel is always available and does not get destroyed after every configuration change.
        If the readerService was inside the Activity, this would have implied that the fragments always need to have a reference
        to their parent activity and this may be a problem.
        It is possible for getActivity() to return null when the fragment is not attached to the activity, this happens when the activity
        has been destroyed due to a configuration change but the fragment still has some callback listener registered.
        Calling a method over the return value of getActivity() would cause a crash of the application.
        The ActivityViewModel class instead is not bound to the lifecycle of the activity, therefore it can always be used and if
        the fragment is performing some operation when the screen is rotated, there is no danger for the app to crash because the reference
        to this class is always valid.

        See also https://stackoverflow.com/a/43748449
     */

    /**
     * Reader service. Reader Service object, we are inserting the service inside the activityViewModel: this way we can share the service
     * between the activity and the fragments. We bind to the service just once in the activity, not in the fragments.
     * https://stackoverflow.com/questions/24309379/bind-service-to-activity-or-fragment
     *
     * Furthermore, binding and unbinding everytime a fragment gets the foreground can be quite expensive resource-wise without any need
     * for doing so, they are all sharing the same Service and they belong to the same activity.
     */
    @SuppressLint("StaticFieldLeak")
    var readerService : ReaderService? = null

    /**
     * Tells us whether the activity is bound to the service. We are keeping this inside the ActivityViewModel because we do not want to tie this variable
     * to the lifecycle of the activity.
     */
    var mBound = false
}