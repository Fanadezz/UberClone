package com.androidshowtime.uberclone.driver


import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.androidshowtime.uberclone.databinding.FragmentDriverBinding
import com.google.android.gms.location.*
import com.google.firebase.firestore.FirebaseFirestore
import timber.log.Timber
import kotlin.math.roundToInt

class DriverFragment : Fragment() {

    //vars
    private lateinit var requestsList: MutableList<String>
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var firestore: FirebaseFirestore
    private lateinit var driverCurrentLocation: Location

    //request location permission
    val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts
            .RequestPermission()
                                                             ) {
        if (it) {

            startLocationUpdates()

        } else {

            Toast.makeText(activity, "Location Permission needed", Toast.LENGTH_SHORT).show()
        }
    }


    //location updates components
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private var locationCallback: LocationCallback = object : LocationCallback() {

        override fun onLocationResult(locationResult: LocationResult?) {
            super.onLocationResult(locationResult)

            if (locationResult != null) {

                locationResult.locations.forEach { driverCurrentLocation = it }

                getAllRideRequests()


            } else {
                //Log Driver's location as null
                Timber.i("Current location is null")
                return
            }
        }


    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
                             ): View? {

        //set initial driver's location before the location updates kicks off avoid crashes
        val loc = Location("")
        loc.latitude = 0.0
        loc.longitude = 0.0
        driverCurrentLocation = loc
        //initialize firestore
        firestore = FirebaseFirestore.getInstance()
        //request permission
        requestLocationPermission.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)

        //initialize fusedLocationProviderClient
        fusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(requireActivity())


        //initialize locationRequest
        locationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 3000
            fastestInterval = 2000
        }


        val binding = FragmentDriverBinding.inflate(inflater)

        (activity as AppCompatActivity).supportActionBar?.show()
        (activity as AppCompatActivity).supportActionBar?.title = "Nearby Requests"

        //initialize requestList
        requestsList = mutableListOf()


        //initialize Adapter
        adapter = ArrayAdapter<String>(
            requireActivity(), android.R.layout
                .simple_list_item_1, requestsList
                                      )

        binding.listView.adapter = adapter

        binding.listView.setOnItemClickListener { _, _, i, _ ->


        }




        return binding.root
    }

    //startLocationUpdates() to be called once permission has been granted
    fun startLocationUpdates() {

        try {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
                                                              )
        } catch (e: SecurityException) {
            //Create a function to request necessary permissions from the app.

        }
    }


    override fun onStop() {
        super.onStop()

        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }

    private fun getAllRideRequests() {

        //get all request documents
        firestore.collection("UserLocation")
            .get()
            .addOnSuccessListener { result ->

                for (requestDocument in result) {


                    val point = requestDocument.getGeoPoint("geoPoint")


                    //set location latitude and longitude
                    val loc = Location("")
                    loc.latitude = point?.latitude!!
                    loc.longitude = point.longitude


                    val documentID = requestDocument.id

                    //using Location class distanceTo()to calculate distance in km
                    val distance = driverCurrentLocation.distanceTo(loc) / 1000





                    populateListWithRequests(documentID, distance)



                }

            }.addOnFailureListener {

                Timber.e(it)
            }

       // clear list
         requestsList.clear()



    }


    private fun populateListWithRequests(

        docId: String,
        distance: Float
                                        ) {




        //filter list to include only locations <50 KM

        if (distance <= 50.0) {
            requestsList.add("$docId \n ${distance.roundToInt()} KM")
            Timber.i("List Size is after population ${requestsList.size}")
        }


        adapter.notifyDataSetChanged()

    }

}

/*  val user: User = Gson().fromJson(requestDocument.get("user").toString(),
                                                    User::class.java)*/
