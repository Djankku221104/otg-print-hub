package com.otgprinthub.driver

import android.content.Context
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VidPidDatabase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    private val vendors: Map<String, VendorInfo> by lazy { loadVendors() }

    data class VendorInfo(val vid: String, val name: String, val fullName: String)

    fun getVendorName(vid: String): String = vendors[vid.lowercase()]?.name ?: "Unknown"

    fun getVendorFullName(vid: String): String = vendors[vid.lowercase()]?.fullName ?: "Unknown Manufacturer"

    fun getVendorInfo(vid: String): VendorInfo? = vendors[vid.lowercase()]

    fun isKnownPrinterVendor(vid: String): Boolean = vendors.containsKey(vid.lowercase())

    private fun loadVendors(): Map<String, VendorInfo> {
        return try {
            val json = context.assets.open("usb_vid_database.json").bufferedReader().readText()
            val db = gson.fromJson(json, VidDatabase::class.java)
            db.vendors.associate { it.vid.lowercase() to VendorInfo(it.vid, it.name, it.full_name) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private data class VidDatabase(val vendors: List<VendorJson> = emptyList())
    private data class VendorJson(val vid: String, val name: String, val full_name: String)
}
