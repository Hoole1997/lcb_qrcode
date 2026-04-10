package com.android.common.bill.ads.util

import android.os.Parcel
import com.android.common.bill.ads.log.AdLogger
import com.google.firebase.FirebaseApp
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.common.internal.safeparcel.SafeParcelReader
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import net.corekit.core.ext.DataStoreStringDelegate
import net.corekit.core.utils.ProviderContext
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.Field

class AdmobReflectionStack(
    val stack: String,
    val field: String,
    val adType: String,
    val stackName: String
) {

    companion object {
        const val BANNER_STACK = "bannerStack"
        const val SP_STACK = "spStack"
        const val IV_STACK = "ivStack"
        const val RV_STACK = "rvStack"
        const val NATIVE_STACK = "nativeStack"
    }

    private val array = stack.split(".")

    fun getStack(): List<String> {
        return array
    }

    fun adFieldName(): String {
        return field
    }
}

/**
 * admob_stack_list ： [{"stack":"zzb.zza.c.g.e.ae","field":"d","adType":"rvStack","stackName":"2026.03.10"},{"stack":"zzc.zza.a.b.d.d.ae","field":"d","adType":"ivStack","stackName":"2026.03.10"}]
 */

object AdmobStackReflectionUtils {
    const val REMOTE_CONFIG_KEY_ADMOB_STACK_LIST = "admob_stack_list"
    private const val LOCAL_CACHE_KEY_ADMOB_STACK_LIST = "admobStackListRemote"

    private val baseStacks = listOf(
        AdmobReflectionStack("zzb.zza.c.g.e.ae", "d", AdmobReflectionStack.RV_STACK, "2026.03.10"),
        AdmobReflectionStack("zzc.zza.a.b.d.d.ae", "d", AdmobReflectionStack.IV_STACK, "2026.03.10"),

        AdmobReflectionStack("zza.zzj.zza.a.a.f.a.e.ae", "d", AdmobReflectionStack.BANNER_STACK, "StackB"),
        AdmobReflectionStack("zzb.zza.a.a.a.e.ae", "d", AdmobReflectionStack.SP_STACK, "StackB"),
        AdmobReflectionStack("zzc.zza.a.a.d.d.ae", "d", AdmobReflectionStack.IV_STACK, "StackB"),
        AdmobReflectionStack("zzb.zza.b.g.e.ae", "d", AdmobReflectionStack.RV_STACK, "StackB"),
        AdmobReflectionStack("zza.zza.b.d.c.ae", "d", AdmobReflectionStack.NATIVE_STACK, "StackB"),

        AdmobReflectionStack("zza.zzj.zza.a.a.f.a.e.ae", "zzd", AdmobReflectionStack.BANNER_STACK, "StackA"),
        AdmobReflectionStack("zzb.zza.zzc.zza.zzk.zzae", "zzd", AdmobReflectionStack.SP_STACK, "StackA"),
        AdmobReflectionStack("zzc.zzj.zzf.zzd.zzae", "zzd", AdmobReflectionStack.IV_STACK, "StackA"),
        AdmobReflectionStack("zzb.zzi.zze.zze.zzae", "zzd", AdmobReflectionStack.RV_STACK, "StackA"),
        AdmobReflectionStack("zza.zzb.zzf.zzD.zzb.zzae", "zzd", AdmobReflectionStack.NATIVE_STACK, "StackA")
    )

    private val list = mutableListOf<AdmobReflectionStack>()
    private val stackMap = mutableMapOf<String, AdmobReflectionStack?>()
    private var remoteStackListJsonCache by DataStoreStringDelegate(LOCAL_CACHE_KEY_ADMOB_STACK_LIST, "")

    @Volatile
    private var remoteStackListJson: String = ""
    @Volatile
    private var initialized = false

    init {
        rebuildStacks()
    }

    fun initialize() {
        val shouldFetch = synchronized(this) {
            if (initialized) {
                false
            } else {
                initialized = true
                updateRemoteStackList(remoteStackListJsonCache)
                true
            }
        }
        if (shouldFetch) {
            fetchRemoteStackList()
        }
    }

    fun updateRemoteStackList(json: String?) {
        val normalized = json?.trim().orEmpty()
        synchronized(this) {
            if (remoteStackListJson == normalized && list.isNotEmpty()) return
            remoteStackListJson = normalized
            rebuildStacksLocked()
        }
    }

    private fun fetchRemoteStackList() {
        try {
            FirebaseApp.initializeApp(ProviderContext.getAppContext())
        } catch (_: Exception) {
            // Firebase may already be initialized; ignore and continue.
        }

        val remoteConfig = try {
            FirebaseRemoteConfig.getInstance()
        } catch (e: Exception) {
            AdLogger.e("AdmobStackReflectionUtils: 获取 FirebaseRemoteConfig 实例失败", e)
            return
        }

        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                AdLogger.e("AdmobStackReflectionUtils: 拉取 admob_stack_list 失败", task.exception)
                return@addOnCompleteListener
            }

            val remoteValue = runCatching {
                remoteConfig.getString(REMOTE_CONFIG_KEY_ADMOB_STACK_LIST)
            }.getOrElse {
                AdLogger.e("AdmobStackReflectionUtils: 读取 admob_stack_list 失败", it)
                ""
            }

            if (remoteValue.isNotBlank()) {
                remoteStackListJsonCache = remoteValue
                updateRemoteStackList(remoteValue)
                AdLogger.d("AdmobStackReflectionUtils: 远程 AdMob 反射栈配置更新成功")
            } else {
                AdLogger.d("AdmobStackReflectionUtils: admob_stack_list 为空，继续使用本地缓存/内置栈")
            }
        }
    }

    private fun rebuildStacks() {
        synchronized(this) {
            rebuildStacksLocked()
        }
    }

    private fun rebuildStacksLocked() {
        list.clear()

        if (remoteStackListJson.isNotEmpty()) {
            try {
                val jsonArray = JSONArray(remoteStackListJson)
                for (i in 0 until jsonArray.length()) {
                    val jsonObject = jsonArray.getJSONObject(i)
                    val stack = jsonObject.optString("stack")
                    val field = jsonObject.optString("field")
                    val adType = jsonObject.optString("adType")
                    val stackName = jsonObject.optString("stackName")
                    list.add(AdmobReflectionStack(stack, field, adType, stackName))

                    logW {
                        String.format(
                            "[AdmobStackReflectionUtils] remote admob stack : stack = %s , field = %s , adType = %s , stackName = %s",
                            stack,
                            field,
                            adType,
                            stackName
                        )
                    }
                }
            } catch (e: Exception) {
                logE { String.format("[AdmobStackReflectionUtils] load remote error. %s", e.message) }
            }
        }

        list.addAll(baseStacks)
        stackMap.clear()
    }

    private fun findStack(adType: String): List<AdmobReflectionStack> {
        return list.filter { it.adType == adType }
    }

    private fun getEcpm(obj: Any, adType: String): Int {
        synchronized(this) {
            if (stackMap.contains(adType) && stackMap[adType] == null) {
                return -1
            }

            val stack = stackMap[adType]

            if (stack != null) {
                return try {
                    getEcpm(obj, stack)
                } catch (e: Exception) {
                    -1
                }
            } else {
                findStack(adType).forEach {
                    try {
                        val ecpm = getEcpm(obj, it)
                        if (ecpm >= 0) {
                            logW {
                                String.format(
                                    "[AdmobStackReflectionUtils] [success] get ecpm ad type : %s , stack : %s , field : %s , stackName = %s",
                                    adType,
                                    it.stack,
                                    it.field,
                                    it.stackName
                                )
                            }
                            stackMap[adType] = it
                            return ecpm
                        }
                    } catch (e: Exception) {
                        logW {
                            String.format(
                                "[AdmobStackReflectionUtils] [fail] get ecpm ad type : %s , stack : %s , field : %s , stackName = %s ; message : %s",
                                adType,
                                it.stack,
                                it.field,
                                it.stackName,
                                e.message ?: ""
                            )
                        }
                    }
                }
            }

            logW {
                String.format(
                    "[AdmobStackReflectionUtils] get ecpm ad type : %s 加载路径失败了，无法获取 revenue",
                    adType
                )
            }

            stackMap[adType] = null

            return -1
        }
    }

    private fun getEcpm(obj: Any, stack: AdmobReflectionStack): Int {
        var temp = obj
        stack.getStack().forEach {
            val value =
                temp.getValue(it) ?: throw RuntimeException(
                    String.format(
                        "stack name = %s , ad type = %s , field = %s",
                        stack.stackName,
                        stack.adType,
                        it
                    )
                )
            temp = value
        }
        return temp.getValue(stack.adFieldName())?.toString()?.toInt() ?: 0
    }

    fun getBannerEcpm(obj: Any): Int {
        return getEcpm(obj, AdmobReflectionStack.BANNER_STACK)
    }

    fun getSpEcpm(obj: Any): Int {
        return getEcpm(obj, AdmobReflectionStack.SP_STACK)
    }

    fun getIvEcpm(obj: Any): Int {
        return getEcpm(obj, AdmobReflectionStack.IV_STACK)
    }

    fun getRvEcpm(obj: Any): Int {
        return getEcpm(obj, AdmobReflectionStack.RV_STACK)
    }

    fun getNativeEcpm(obj: Any): Int {
        return getEcpm(obj, AdmobReflectionStack.NATIVE_STACK)
    }

    fun getBannerEcpmMicros(obj: Any): Long {
        return getValueMicros(getBannerEcpm(obj), obj)
    }

    fun getSpEcpmMicros(obj: Any): Long {
        return getValueMicros(getSpEcpm(obj), obj)
    }

    fun getIvEcpmMicros(obj: Any): Long {
        return getValueMicros(getIvEcpm(obj), obj)
    }

    fun getRvEcpmMicros(obj: Any): Long {
        return getValueMicros(getRvEcpm(obj), obj)
    }

    fun getNativeEcpmMicros(obj: Any): Long {
        return getValueMicros(getNativeEcpm(obj), obj)
    }

    private fun getValueMicros(stackValue: Int, obj: Any): Long {
        if (stackValue >= 0) return stackValue.toLong()
        return ecpm(obj)
    }

    fun Any.getValue(fieldName: String): Any? {
        try {
            var clazz: Class<*>? = this::class.java
            var field: Field? = null
            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName)
                    field.isAccessible = true
                    break
                } catch (e: NoSuchFieldException) {
                    clazz = clazz.superclass
                }
            }
            if (field != null) {
                return field.get(this)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun Any.findAdValueObject(
        objArray: MutableList<Any> = mutableListOf(),
        stackArray: Array<String> = arrayOf(),
        maxDepth: Int = 50,
    ): Pair<Any?, Array<String>>? {
        if (stackArray.size >= maxDepth) {
            return null
        }

        return try {
            var clazz: Class<*>? = this::class.java
            while (clazz != null) {
                clazz.declaredFields.forEach { field ->
                    field.isAccessible = true
                    val obj = try {
                        field.get(this)
                    } catch (_: Exception) {
                        return@forEach
                    }

                    if (obj is String && obj == "USD") {
                        return Pair(this, stackArray)
                    }

                    val fieldTypeName = field.type.name
                    if (fieldTypeName != clazz.name &&
                        (fieldTypeName.startsWith("com.google.android.gms") ||
                            fieldTypeName == "android.os.IBinder")
                    ) {
                        if (obj != null && obj !in objArray) {
                            objArray.add(obj)
                            val newStack = stackArray + field.name
                            val result = obj.findAdValueObject(objArray, newStack, maxDepth)
                            if (result != null) {
                                return result
                            }
                        }
                    }
                }
                clazz = clazz.superclass
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun ecpm(obj: Any): Long {
        val findAdValueObject = obj.findAdValueObject()
        val first = findAdValueObject?.first
        return parcelAdValue(first)
    }

    fun ResponseInfo.getMediationGroupName(): String {
        try {
            val responseInfoObj = JSONObject(this.toString())
            val responseExtras: JSONObject? = responseInfoObj.optJSONObject("Response Extras")
            if (responseExtras != null) {
                return responseExtras.optString("mediation_group_name", "")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ""
    }
}

private fun parcelAdValue(first: Any?): Long {
    try {
        first?.let {
            val writeToParcelMethod = it::class.java.getDeclaredMethod("writeToParcel", Parcel::class.java, Int::class.java)
            writeToParcelMethod.isAccessible = true
            val parcel = Parcel.obtain()
            try {
                writeToParcelMethod.invoke(it, parcel, 0)
                parcel.setDataPosition(0)
                val validateObjectHeader = SafeParcelReader.validateObjectHeader(parcel)
                var typeNum = 0
                var precisionNum = 0
                var currency = ""
                var value = 0L
                while (parcel.dataPosition() < validateObjectHeader) {
                    val readHeader = SafeParcelReader.readHeader(parcel)
                    val fieldId = SafeParcelReader.getFieldId(readHeader)
                    when (fieldId) {
                        1 -> {
                            typeNum = SafeParcelReader.readInt(parcel, readHeader)
                        }

                        2 -> {
                            precisionNum = SafeParcelReader.readInt(parcel, readHeader)
                        }

                        3 -> {
                            currency = SafeParcelReader.createString(parcel, readHeader)
                        }

                        4 -> {
                            value = SafeParcelReader.readLong(parcel, readHeader)
                        }

                        else -> {
                            SafeParcelReader.skipUnknownField(parcel, readHeader)
                        }
                    }
                }
                SafeParcelReader.ensureAtEnd(parcel, validateObjectHeader)
                return value
            } finally {
                parcel.recycle()
            }
        }

    } catch (e: Exception) {
        e.printStackTrace()
    }
    return 0
}

private inline fun logW(message: () -> String) {
    AdLogger.w(message())
}

private inline fun logE(message: () -> String) {
    AdLogger.e(message())
}
