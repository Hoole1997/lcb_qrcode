package net.corekit.core.utils

import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts

class ActivityLauncher(activityResultCaller: ActivityResultCaller) {

    //region 权限
    private var permissionCallback: ActivityResultCallback<Map<String, Boolean>>? = null
    private val permissionLauncher =
        activityResultCaller.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result: Map<String, Boolean> ->
            permissionCallback?.onActivityResult(result)
        }

    fun launch(
        permissionArray: Array<String>,
        permissionCallback: ActivityResultCallback<Map<String, Boolean>>?
    ) {
        this.permissionCallback = permissionCallback
        permissionLauncher.launch(permissionArray)
    }

    //endregion

    //region intent跳转
    private var activityResultCallback: ActivityResultCallback<ActivityResult>? = null
    private val intentLauncher =
        activityResultCaller.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult: ActivityResult ->
            activityResultCallback?.onActivityResult(activityResult)
        }

    /**
     * it.resultCode == Activity.RESULT_OK
     */
    fun launch(
        intent: Intent,
        activityResultCallback: ActivityResultCallback<ActivityResult>? = null
    ) {
        this.activityResultCallback = activityResultCallback
        intentLauncher.launch(intent)
    }
    //endregion


    //region saf
//    private var safResultCallback: ActivityResultCallback<Uri?>? = null
//    private val safLauncher =
//        activityResultCaller.registerForActivityResult(
//            ActivityResultContracts.OpenDocument(),
//        ) { uri ->
//            safResultCallback?.onActivityResult(uri)
//        }
//
//    fun launch(array: Array<String>, safResultCallback: ActivityResultCallback<Uri?>?) {
//        this.safResultCallback = safResultCallback
//        safLauncher.launch(array)
//    }
    //end region


}
