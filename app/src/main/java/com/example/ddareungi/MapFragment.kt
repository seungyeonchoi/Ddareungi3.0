package com.example.ddareungi


import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import com.example.ddareungi.dataClass.MyBike
import com.example.ddareungi.dataClass.MyPark
import com.example.ddareungi.dataClass.MyRestroom
import com.example.ddareungi.dataClass.Rental
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import io.github.yavski.fabspeeddial.SimpleMenuListenerAdapter
import kotlinx.android.synthetic.main.fragment_map.*
import java.net.URLEncoder
import java.util.*


class MapFragment : Fragment(), OnMapReadyCallback, View.OnClickListener {
    var dbHandler: MyDB? = null
    lateinit var mMap: GoogleMap
    var mapView: MapView? = null    //GoogleMap을 보여주는 MapView
    var mLocationPermissionGranted = false  //GPS 권환 획득 유무를 확인하는 flag 값
    var mEnableGPS = false
    lateinit var fusedLocationClient: FusedLocationProviderClient   //휴대폰이 마지막으로 얻은 내 위치를 얻어오기 위한 객체
    private val KONKUK_UNIV = LatLng(37.540, 127.07)
    private val DEFAULT_ZOOM = 16f
    lateinit var mBikeList: MutableList<MyBike>
    lateinit var mToiletList: MutableList<MyRestroom>
    lateinit var mParkList: MutableList<MyPark>
    val visibleMarkers = mutableMapOf<String, Marker>()
    lateinit var markerController: MarkerController
    var searchedPlaceMarker: Marker? = null
    var currentMarkerType = PlaceType.BIKE
    var clickedMarker: Marker? = null
    var myLocation: Location? = null
    var fromBookmarkFragment = false
    var mSentBikeName: String? = null

    enum class PlaceType {
        BIKE, TOILET, PARK, SEARCH
    }

    fun setData(
        locationPermissionGranted: Boolean,
        enableGPS: Boolean,
        bikeList: MutableList<MyBike>,
        toiletList: MutableList<MyRestroom>,
        parkList: MutableList<MyPark>,
        sentBikeName: String?
    ) {
        mLocationPermissionGranted = locationPermissionGranted
        mEnableGPS = enableGPS
        mBikeList = bikeList
        mToiletList = toiletList
        mParkList = parkList
        mSentBikeName = sentBikeName
        if (mSentBikeName != null)
            fromBookmarkFragment = true
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_map, container, false)
        mapView = view.findViewById(R.id.mapView)
        mapView!!.onCreate(savedInstanceState)
        mapView!!.getMapAsync(this)

        return view
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!
        markerController = MarkerController(context!!, mMap, visibleMarkers)
        dbHandler = MyDB(context!!)
        visibleMarkers.clear()

        mMap.setMinZoomPreference(14f)

        if (fromBookmarkFragment) {
            for (bike in mBikeList) {
                if (bike.stationName == mSentBikeName) {
                    clickedMarker = markerController.addBikeMarker(bike)
                    clickedMarker!!.tag = bike
                    adjustMapWidget(clickedMarker!!, bike, PlaceType.BIKE)
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(clickedMarker!!.position))
                }
            }
        }

        if (mLocationPermissionGranted && mEnableGPS) {
            fusedLocationClient.lastLocation.addOnSuccessListener {
                if (!fromBookmarkFragment) {
                    mMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(it.latitude, it.longitude),
                            DEFAULT_ZOOM
                        )
                    )
                }
                myLocation = it
            }
            mMap.isMyLocationEnabled = true
            my_location_button.setOnClickListener {
                fusedLocationClient.lastLocation.addOnSuccessListener {
                        mMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(it.latitude, it.longitude),
                                DEFAULT_ZOOM
                            ), 500, null
                        )
                    
                }
            }
        } else {
            //GPS 권한이 없는 경우 지도 초기 값을 건국대학교 위치로 설정
            mMap.isMyLocationEnabled = false
            dest_dist_text.visibility = View.GONE
            mMap.moveCamera(CameraUpdateFactory.newLatLng(KONKUK_UNIV))
        }

        mMap.setOnCameraMoveListener {
            updateMarker(currentMarkerType, false)
        }
        mMap.setOnCameraIdleListener {
            updateMarker(currentMarkerType, false)
        }

        mMap.setOnMarkerClickListener {
            val clickedMarkerTag = it.tag
            clickedMarker = it
            when (clickedMarkerTag) {
                is MyBike -> adjustMapWidget(it, clickedMarkerTag, PlaceType.BIKE)
                is MyRestroom -> adjustMapWidget(it, clickedMarkerTag, PlaceType.TOILET)
                is MyPark -> adjustMapWidget(it, clickedMarkerTag, PlaceType.PARK)
                is Place -> adjustMapWidget(it, clickedMarkerTag, PlaceType.SEARCH)
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLng(it.position))
            true
        }

        mMap.setOnMapClickListener {
            map_card_view.visibility = View.GONE
            dest_card_view.visibility = View.GONE
            if (clickedMarker != null)
                clickedMarker!!.hideInfoWindow()
            if (searchedPlaceMarker != null) {
                searchedPlaceMarker!!.remove()
            }
            map_refresh_fab.show()
            map_place_fab.show()
        }


    }


    private fun adjustMapWidget(marker: Marker, content: Any?, markerType: MapFragment.PlaceType) {
        lateinit var widgetContent: Any

        when (markerType) {
            MapFragment.PlaceType.BIKE -> {
                widgetContent = content as MyBike
                map_refresh_fab.hide()
                map_place_fab.hide()
                dest_card_view.visibility = View.GONE
                map_card_view.visibility = View.VISIBLE
                map_card_title_text.text = widgetContent.stationName
                map_card_regular_text.text = "${widgetContent.parkingBikeTotCnt}대 사용 가능"

                if (dbHandler!!.findOffice(widgetContent.stationName) == 0) {
                    bookmark_button.setImageDrawable(
                        context!!.resources.getDrawable(
                            R.drawable.ic_star_border_black_24dp,
                            null
                        )
                    )
                } else {
                    bookmark_button.setImageDrawable(
                        context!!.resources.getDrawable(
                            R.drawable.ic_star_black_24dp,
                            null
                        )
                    )
                }
                bookmark_button.setOnClickListener {
                    if (dbHandler!!.findOffice((widgetContent as MyBike).stationName) == 0) {
                        val rental = Rental("", "", 0)
                        val success: Boolean
                        rental.rental_office = (widgetContent as MyBike).stationName
                        rental.bookmarked = 1
                        success = dbHandler!!.addUser(rental)
                        if (success) {
                            bookmark_button.setImageDrawable(
                                context!!.resources.getDrawable(
                                    R.drawable.ic_star_black_24dp,
                                    null
                                )
                            )
                            (widgetContent as MyBike).bookmarked = 1
                        }
                    } else {
                        val rental = Rental("", "", 1)

                        rental.delete = (widgetContent as MyBike).stationName
                        rental.bookmarked = 0
                        dbHandler!!.deleteUser(rental)
                        bookmark_button.setImageDrawable(
                            context!!.resources.getDrawable(
                                R.drawable.ic_star_border_black_24dp,
                                null
                            )
                        )
                        (widgetContent as MyBike).bookmarked = 1
                    }
                }
            }
            MapFragment.PlaceType.TOILET -> {
                marker.showInfoWindow()
            }
            MapFragment.PlaceType.PARK -> {
                widgetContent = content as MyPark
                map_refresh_fab.hide()
                map_place_fab.hide()
                map_card_view.visibility = View.GONE
                dest_card_view.visibility = View.VISIBLE
                dest_name_text.text = widgetContent.name

                if (myLocation != null) {
                    val dest = Location("dest")
                    dest.latitude = widgetContent.latitude
                    dest.longitude = widgetContent.longitude
                    val dist = myLocation!!.distanceTo(dest) / 1000
                    val distStr = String.format("%.1fkm", dist)
                    dest_dist_text.text = distStr
                }
            }
            MapFragment.PlaceType.SEARCH -> {
                widgetContent = content as Place
                map_refresh_fab.hide()
                map_place_fab.hide()
                map_card_view.visibility = View.GONE
                dest_card_view.visibility = View.VISIBLE
                dest_name_text.text = widgetContent.name

                if (myLocation != null) {
                    val dest = Location("dest")
                    dest.latitude = widgetContent.latLng!!.latitude
                    dest.longitude = widgetContent.latLng!!.longitude
                    val dist = myLocation!!.distanceTo(dest) / 1000
                    val distStr = String.format("%.1fkm", dist)
                    dest_dist_text.text = distStr
                }
            }
        }
    }

    fun updateMarker(markerType: PlaceType, clearAll: Boolean) {
        if (clearAll) {
            mMap.clear()
            visibleMarkers.clear()
        }

        val bounds = mMap.projection.visibleRegion.latLngBounds

        when (markerType) {
            PlaceType.BIKE -> {
                for (bikeStop in mBikeList) {
                    if (bounds.contains(LatLng(bikeStop.stationLatitude, bikeStop.stationLongitude))) {
                        if (!visibleMarkers.containsKey(bikeStop.stationId)) {
                            if (mMap.cameraPosition.zoom >= 15f) {
                                visibleMarkers[bikeStop.stationId] = markerController.addBikeMarker(bikeStop)
                                visibleMarkers[bikeStop.stationId]!!.tag = bikeStop
                            } else {
                                if (bikeStop.parkingBikeTotCnt > 0) {
                                    visibleMarkers[bikeStop.stationId] = markerController.addBikeMarker(bikeStop)
                                    visibleMarkers[bikeStop.stationId]!!.tag = bikeStop
                                }
                            }
                        } else {
                            if (mMap.cameraPosition.zoom < 15f && bikeStop.parkingBikeTotCnt == 0) {
                                markerController.removeMarker(bikeStop.stationId)
                            }
                        }
                    }
                }
            }
            PlaceType.TOILET -> {
                for (toilet in mToiletList) {
                    if (bounds.contains(LatLng(toilet.wgs84_y, toilet.wgs84_x))) {
                        if (!visibleMarkers.containsKey(toilet.fName)) {
                            visibleMarkers[toilet.fName] = markerController.addToiletMarker(toilet)
                            visibleMarkers[toilet.fName]!!.tag = toilet
                            visibleMarkers[toilet.fName]!!.title = toilet.fName
                        }
                    }
                }
            }
            PlaceType.PARK -> {
                for (park in mParkList) {
                    if (bounds.contains(LatLng(park.latitude, park.longitude))) {
                        if (!visibleMarkers.containsKey(park.id.toString())) {
                            visibleMarkers[park.id.toString()] = markerController.addParkMarker(park)
                            visibleMarkers[park.id.toString()]!!.tag = park
                        }
                    }
                }
            }
        }
    }

    private fun findClosetBike(place: Place?, park: MyPark?): MyBike {

        val dest = Location("dest")
        if(place != null) {
            dest.latitude = place.latLng!!.latitude
            dest.longitude = place.latLng!!.longitude
        } else {
            dest.latitude = park!!.latitude
            dest.longitude = park.longitude
        }
        var closestBike: MyBike = mBikeList[0]
        var dist = Float.MAX_VALUE

        for (bike in mBikeList) {
            val bikeStop = Location("bike")
            bikeStop.latitude = bike.stationLatitude
            bikeStop.longitude = bike.stationLongitude
            var tempDist: Float
            tempDist = dest.distanceTo(bikeStop)
            if (dist > tempDist) {
                dist = tempDist
                closestBike = bike
            }
        }
        return closestBike
    }

    override fun onClick(v: View?) {
        val dlat: Double
        val dlng: Double
        val dname: String
        var url: String = ""
        if (v!!.id == R.id.path_button) {
            dlat = clickedMarker!!.position.latitude
            dlng = clickedMarker!!.position.longitude
            dname = URLEncoder.encode((clickedMarker!!.tag as MyBike).stationName, "UTF-8")
            url =
                "nmap://route/walk?dlat=$dlat&dlng=$dlng&dname=$dname&appname=com.example.ddareungi"
        }else if (v.id == R.id.dest_card_path_button && searchedPlaceMarker != null) {
            dlat = searchedPlaceMarker!!.position.latitude
            dlng = searchedPlaceMarker!!.position.longitude
            dname = URLEncoder.encode((searchedPlaceMarker!!.tag as Place).name, "UTF-8")

            val closetBike = findClosetBike(searchedPlaceMarker!!.tag as Place, null)
            val vlat = closetBike.stationLatitude
            val vlng = closetBike.stationLongitude
            val vname = URLEncoder.encode(closetBike.stationName, "UTF-8")
            url =
                "nmap://route/bicycle?dlat=$dlat&dlng=$dlng&dname=$dname&v1lat=$vlat&v1lng=$vlng&v1name=$vname&appname=com.example.ddareungi"
        } else if(v.id == R.id.dest_card_path_button && searchedPlaceMarker == null) {
            dlat = clickedMarker!!.position.latitude
            dlng = clickedMarker!!.position.longitude
            dname = URLEncoder.encode((clickedMarker!!.tag as MyPark).name, "UTF-8")
            url =
                "nmap://route/walk?dlat=$dlat&dlng=$dlng&dname=$dname&appname=com.example.ddareungi"

            val closetBike = findClosetBike(null, clickedMarker!!.tag as MyPark)
            val vlat = closetBike.stationLatitude
            val vlng = closetBike.stationLongitude
            val vname = URLEncoder.encode(closetBike.stationName, "UTF-8")
            url =
                "nmap://route/bicycle?dlat=$dlat&dlng=$dlng&dname=$dname&v1lat=$vlat&v1lng=$vlng&v1name=$vname&appname=com.example.ddareungi"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addCategory(Intent.CATEGORY_BROWSABLE)
        val list: MutableList<ResolveInfo> =
            activity!!.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        if (list.isEmpty()) {
            context!!.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.nhn.android.nmap")
                )
            )
        } else {
            context!!.startActivity(intent)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context!!)
        if (!(mLocationPermissionGranted && mEnableGPS))
            my_location_button.hide()

        map_refresh_fab.setOnClickListener {
            val url= "http://openapi.seoul.go.kr:8088/746c776f61627a7437376b49567a68/json/bikeList/"
            val networkTask= MainActivity.NetworkTask(0,url, null, activity as MainActivity,true)
            networkTask.execute()
        }

        path_button.setOnClickListener(this)

        dest_card_path_button.setOnClickListener(this)

        rent_button.setOnClickListener {
            val ddareungiHome = Uri.parse("https://www.bikeseoul.com")
            val webIntent = Intent(Intent.ACTION_VIEW, ddareungiHome)
            startActivity(webIntent)
        }

        map_place_fab.setMenuListener(object : SimpleMenuListenerAdapter() {
            override fun onMenuItemSelected(menuItem: MenuItem?): Boolean {
                when (menuItem!!.itemId) {
                    R.id.bike_stop_fab -> {
                        if (currentMarkerType != PlaceType.BIKE) {
                            currentMarkerType = PlaceType.BIKE
                            updateMarker(currentMarkerType, true)
                        }
                    }
                    R.id.toilet_fab -> {
                        if (currentMarkerType != PlaceType.TOILET) {
                            currentMarkerType = PlaceType.TOILET
                            updateMarker(currentMarkerType, true)
                        }
                    }
                    R.id.park_fab -> {
                        if (currentMarkerType != PlaceType.PARK) {
                            currentMarkerType = PlaceType.PARK
                            updateMarker(currentMarkerType, true)
                        }
                    }
                }

                return false
            }
        })
        Places.initialize(context!!, resources.getString(R.string.google_maps_key))
        initPlaceSearch()
    }


    private fun initPlaceSearch() {
        val placesClient = Places.createClient(context!!)

        var placeId = ""
        val placeFields = Arrays.asList(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val bounds = LatLngBounds(LatLng(37.413294, 126.734086), LatLng(37.715133, 127.269311))
        val rectBounds = RectangularBounds.newInstance(bounds)

        val autocompleteFragment =
            childFragmentManager.findFragmentById(R.id.autocomplete_fragment) as AutocompleteSupportFragment?
        autocompleteFragment!!.setHint("목적지 검색")
        autocompleteFragment.setPlaceFields(Arrays.asList(Place.Field.ID, Place.Field.NAME))
        autocompleteFragment.setLocationRestriction(rectBounds)

        autocompleteFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                Log.i("place search", "Place : " + place.name + ", " + place.id)
                placeId = place.id.toString()

                val request = FetchPlaceRequest.builder(placeId, placeFields).build()
                placesClient.fetchPlace(request).addOnSuccessListener {
                    val place = it.place
                    Log.i("place search", "Place found " + place.name + ", " + place.latLng.toString())
                    searchedPlaceMarker = markerController.addSearchMarker(place)
                    searchedPlaceMarker!!.tag = place
                    adjustMapWidget(searchedPlaceMarker!!, place, PlaceType.SEARCH)
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(searchedPlaceMarker!!.position))

                    
                }.addOnFailureListener {
                    Log.e("place search", "Place not found: " + it.message)
                }
            }

            override fun onError(p0: Status) {
                Log.i("place search", "An error occurred: $p0")
            }

        })
    }

    override fun onStart() {
        super.onStart()
        mapView!!.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView!!.onResume()
        (activity as AppCompatActivity).supportActionBar!!.hide()
    }


    override fun onPause() {
        super.onPause()
        mapView!!.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView!!.onStop()
        (activity as AppCompatActivity).supportActionBar!!.show()
        fromBookmarkFragment = false
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView!!.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView!!.onSaveInstanceState(outState)
    }


    override fun onLowMemory() {
        super.onLowMemory()
        mapView!!.onLowMemory()
    }

}
