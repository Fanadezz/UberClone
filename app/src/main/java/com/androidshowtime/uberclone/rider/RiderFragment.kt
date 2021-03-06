package com.androidshowtime.uberclone.rider

import android.annotation.SuppressLint
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.androidshowtime.uberclone.MyViewModel
import com.androidshowtime.uberclone.R
import com.androidshowtime.uberclone.databinding.FragmentRiderBinding
import com.androidshowtime.uberclone.model.UberRequest
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Source
import timber.log.Timber
import java.math.RoundingMode
import java.util.*

class RiderFragment : Fragment() {
    //vars
    private lateinit var map: GoogleMap
    private lateinit var currentRiderLocation: Location
    private lateinit var firestore: FirebaseFirestore
    private lateinit var currentUserId: String
    private var isButtonClicked = false
    private lateinit var docID: String
    private lateinit var handler: Handler
    private var isRequestAccepted = false
    private var requestUpdates = true
    private lateinit var binding: FragmentRiderBinding
    private lateinit var infoTextView: TextView
    private var locationForDriver: Location? = null


    //vals
    private val args: RiderFragmentArgs by navArgs()

    // Single Permission Contract
    @SuppressLint("MissingPermission")
    val reqPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {

        if (it) {
            Timber.i("Location Permission Granted")

            //request for Location Updates
            startLocationUpdates()
        }
        else {
            Toast.makeText(activity, "Location Permission Needed", Toast.LENGTH_SHORT).show()
        }


    }


    //components for locations request
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest

    //initialize locationCallback to get notification
    private val locationCallback = object : LocationCallback() {

        //Called when the device location is available.
        override fun onLocationResult(locationResult: LocationResult?) {
            super.onLocationResult(locationResult)


            if (locationResult != null) {
                //locationResult.locations.forEach { currentLocation = it }
                currentRiderLocation = locationResult.lastLocation
                Timber.i(
                        "Current Place:  ${currentRiderLocation.latitude}, ${currentRiderLocation.longitude}"
                        )

                //positioning the camera and the marker
                moveMarkerAndCamera(currentRiderLocation)


            }
            else {
                //log Location as null
                Timber.i("Location is null!!!")
                return

            }


        }


    }

    /*onMapReadyCallBack - is triggered when the map is ready to be used
  and provides a non-null instance of GoogleMap.*/


    private val onMapReadyCallback = OnMapReadyCallback {
        //initialize map to it
        map = it

        //request Permission
        reqPerm.launch(android.Manifest.permission.ACCESS_FINE_LOCATION)


    }


    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
                             ): View? {
        binding = FragmentRiderBinding.inflate(inflater)
        val viewModel = ViewModelProvider(this).get(MyViewModel::class.java)
        binding.viewModel = viewModel

        //initializing the handler
        handler = Handler(Looper.getMainLooper())
        //initializing the fusedLocationProviderClient
        fusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(requireActivity())

        //initializing locationRequest
        locationRequest = LocationRequest().apply {


            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            interval = 3000
            fastestInterval = 2000
        }


        //showDriverArrivalNotification()
        //initializing Firestore
        firestore = FirebaseFirestore.getInstance()

        //intialize TextView

        infoTextView = binding.infoTextView

        //make the textView invisible at first
        infoTextView.visibility = View.INVISIBLE
        //initialize current user
        currentUserId = FirebaseAuth.getInstance().uid!!

        //Call Uber Button implementation
        binding.callUberButton.setOnClickListener {
            requestUpdates = true
            startLocationUpdates()

            //Uber Request
            if (!isButtonClicked) {
                //create Firestore Geopoint variable from currentLocation
                val geoPoint =
                        GeoPoint(currentRiderLocation.latitude, currentRiderLocation.longitude)


                //generate random UUID for using in deleting the document
                docID = UUID.randomUUID().toString()
                //create uberRequest object with 3 arguments
                val uberRequest = UberRequest(docID, geoPoint, isRequestAccepted, Date())
                Timber.i("originalU-loc $uberRequest")
                //save userLocation on firestore
                firestore.collection("UberRequest").document(docID).set(uberRequest)

                        .addOnSuccessListener {

                            //get the document id

                            Toast.makeText(activity, "Uber Requested", Toast.LENGTH_SHORT)
                                    .show()
                        }.addOnFailureListener {

                            Timber.i("Error in saving User's location: $it")
                        }


                binding.callUberButton.text = getString(R.string.cancel_uber_request)
                isButtonClicked = true


                //method that we can run after 2 secs
                checkForUpdates()


            }
            //Uber Request Cancellation
            else {


                //delete document request from firestore
                firestore.collection("UberRequest")
                        .document(docID)
                        .delete()
                        .addOnSuccessListener {

                            Timber.i("$docID deleted")
                            Toast.makeText(activity, "Request Cancelled", Toast.LENGTH_SHORT).show()
                        }


                binding.callUberButton.text = getString(R.string.request_uber)
                isButtonClicked = false
            }
        }


        //log out button implementation
        binding.logOutButton.setOnClickListener {
            findNavController().navigate(RiderFragmentDirections.actionRiderFragmentToLoginFragment())
            FirebaseAuth.getInstance().signOut()


        }

        return binding.root
    }

    private fun checkForUpdates() {


        if (requestUpdates) {

            //check if the request has been accepted by the driver

            //create a document reference
            val docRef = firestore
                    .collection("UberRequest")
                    .document(docID)

            //use get() to retrieve the document specified by docID variable
            docRef.get(Source.SERVER)
                    .addOnSuccessListener { documentSnapshot ->
                        //if documentSnapshot is not null read value of isRequestAccepted
                        if (documentSnapshot != null) {

                            //retrieve firestore's saved value for accepted field
                            val isAccepted = documentSnapshot.get("accepted")!!
                            isRequestAccepted = isAccepted as Boolean
                            /*val uberRequest = documentSnapshot.toObject(UberRequest::class.java)

                        //null check on UberRequest object
                        uberRequest?.let {
                            //set the value of isAccepted to server's value
                            isRequestAccepted = uberRequest.accepted
                        }*/
                        }


                    }.addOnFailureListener { Timber.i("Document not found") }






            if (isRequestAccepted) {

                //show infoTextView
                infoTextView.visibility = View.VISIBLE


                handler.postDelayed({


                                        //display driver's distance from the rider
                                        showDriverInfo()
                                    },3000)


                //make callUberButton INVISIBLE
                binding.callUberButton.visibility = View.INVISIBLE
            }
            //handler to call checkForUpdates() after every 5 secs
            handler.postDelayed(

                    {



                        checkForUpdates()
                    }, 3000)


        }

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(onMapReadyCallback)

    }


    //start Location Updates
    private fun startLocationUpdates() {
        try {
            fusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback,
                    Looper.getMainLooper()
                                                              )
        }
        catch (e: SecurityException) {
            //Create a function to request necessary permissions from the app.

        }
    }


    override fun onPause() {
        super.onPause()

        //remove location updates in background
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
    }


    override fun onStop() {
        super.onStop()


    }

    //move camera around
    fun moveMarkerAndCamera(location: Location) {
        //clear map before setting the marker
        map.clear()

        //obtain currentLatLng from the currentLocation
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val builder = LatLngBounds.builder()

        //create rider's marker
        val riderMarker = map.addMarker(
                MarkerOptions().position(currentLatLng)
                        .title(resources.getString(R.string.rider_marker))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))

        //make info window persist on the map
        riderMarker.showInfoWindow()


        //create driver's marker if location for driver is non-null

        if (locationForDriver != null) {

            val driverLatLng = LatLng(locationForDriver!!.latitude, locationForDriver!!.longitude)
            val driverMarker = map.addMarker(
                    MarkerOptions().position(driverLatLng)
                            .title("driver")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)))


            //make info window persist on the map
            driverMarker.showInfoWindow()

            builder.include(driverMarker.position)
        }

        builder.include(riderMarker.position)


        //generate bounds
        val bounds = builder.build()

        //CameraUpdateFactory
        val cu = CameraUpdateFactory.newLatLngBounds(bounds, 200)
        map.moveCamera(cu)


    }

    //show how far the driver is
    private fun showDriverInfo() {

        val driverLocation = Location("")
        //get the driver who has accepted the request by filtering with their activationId
        firestore.collection("Driver")
                .whereEqualTo("driverActivationId", docID)
                .get()
                .addOnSuccessListener { querySnapshot ->


                    for (doc in querySnapshot) {


                        val geoPoint = doc.getGeoPoint("geoPoint")!!

                        //set the returned geoPoint as the driver's location
                        driverLocation.apply {

                            latitude = geoPoint.latitude
                            longitude = geoPoint.longitude
                        }

                        locationForDriver = driverLocation
                        val distance =
                                calculateDistanceBetween(currentRiderLocation, driverLocation)

                        //prompt the dialog if the driver is less than 0.1 KM away
                        if (distance < 0.3) {

                            showDriverArrivalNotification()
                        }

                        //use string resource to set textView's text
                        infoTextView.text =
                                resources.getString(R.string.driver_on_the_way, distance)


                    }

                    //failure listener
                }.addOnFailureListener {

                    Timber.i("Error in locating a driver - $it")
                }


    }


    private fun calculateDistanceBetween(startPoint: Location, endPoint: Location): Double {


        val distance = startPoint.distanceTo(endPoint) / 1000


        //rounding to one decimal place
        return distance.toBigDecimal().setScale(1, RoundingMode.UP).toDouble()

    }

    //reset to enable rider make another request
    private fun reset() {

        //set requestUpdates to stop handler running again
        requestUpdates = false
        isRequestAccepted = false
        //delete the previous uberRequest
        firestore.collection("UberRequest").document(docID).delete().addOnSuccessListener {

            Timber.i("UberRequest Deleted")
        }

        binding.callUberButton.visibility = View.VISIBLE
        binding.callUberButton.text = getString(R.string.request_uber)
        isButtonClicked = false
        binding.infoTextView.visibility = View.INVISIBLE


    }


    private fun showDriverArrivalNotification() {
        val materialAlertDialogBuilder = MaterialAlertDialogBuilder(requireContext())

                .setTitle("Driver")
                .setMessage("Your Driver has Arrived")
                .setIcon(R.drawable.driver_icon)
                .setPositiveButton("OK") { dialogInterface, _ ->
                    reset()

                    //dismiss the dialog
                    dialogInterface.dismiss()
                }
                .show()
    }


}


