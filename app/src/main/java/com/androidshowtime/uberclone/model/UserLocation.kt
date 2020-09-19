package com.androidshowtime.uberclone.model


import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.*


data class UserLocation(

    val geoPoint: GeoPoint? = null,
        /*ServerTimestamp is unique to firestore. What it does is that if you pass
        * null to the timestamp when you insert this object into the database it
        * will automatically insert a timestamp of the exact time that it was
        * created*/
    @ServerTimestamp
    val timestamp: Date? = null,

    //add a variable to see if the requeste is accepted
    val isRequestAccepted: Boolean = false) {


    override fun toString(): String {
        return "{geopoint: $geoPoint, timestamp: $timestamp, isRequestAccepted: $isRequestAccepted}"
    }


}