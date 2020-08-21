package com.example.ddareungi.bookmark


import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.ddareungi.EventObserver
import com.example.ddareungi.MainActivity
import com.example.ddareungi.R
import com.example.ddareungi.data.StationRe
import com.example.ddareungi.databinding.BookmarkFragBinding
import com.example.ddareungi.utils.NetworkUtils
import com.example.ddareungi.utils.setupSnackBar
import com.example.ddareungi.viewmodel.WeatherViewModel
import com.google.android.gms.location.LocationServices
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.toolbar_layout.*
import java.io.IOException
import java.util.*


class BookmarkFragment : Fragment(), BookmarksAdapter.BookmarkViewHolder.Delegate {


    lateinit var binding: BookmarkFragBinding

    private val bookmarksAdapter by lazy { BookmarksAdapter(this) }

    private lateinit var itemTouchHelper: ItemTouchHelper

    private val mainViewModel = (requireActivity() as MainActivity).mainViewModel
    private lateinit var weatherViewModel: WeatherViewModel


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {

        weatherViewModel = activity?.run {
            ViewModelProviders.of(this)[WeatherViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

         binding = BookmarkFragBinding.inflate(inflater, container, false)
            .apply {
                mainVM = mainViewModel
                weatherVM = weatherViewModel
            }

        return binding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        binding.lifecycleOwner = this.viewLifecycleOwner

        initBookmarksAdapter()
        initObserver()
        setupRefreshFab()
        initSnackBar()

        if(!weatherViewModel.loadSucceed.value!!) {
            getLocationAndWeather()
        }
    }

    private fun initObserver() {
        viewLifecycleOwner.apply {
            mainViewModel.bookmarkedStations.observe(this, Observer {
                bookmarksAdapter.submitList(it)
            })
            mainViewModel.navigateToMapFragEvent.observe(this, EventObserver {
                (activity as MainActivity).apply {
                    setMapFragInstance(it)
                    supportFragmentManager.beginTransaction().replace(R.id.frag_container, mapFragment!!).commit()
                }
            })

        }
    }

    private fun initBookmarksAdapter() {
//        val viewModel = binding.bikeStationVM
//        if(viewModel != null) {
//            itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback(viewModel, bookmarksAdapter, 0, ItemTouchHelper.LEFT))
//            binding.bookmarkRecyclerView.adapter = bookmarksAdapter
//            binding.bookmarkRecyclerView
//                .addItemDecoration(DividerItemDecoration(requireContext(), RecyclerView.VERTICAL))
//            itemTouchHelper.attachToRecyclerView(binding.bookmarkRecyclerView)
//        } else {
//            Log.v("adapter", "ViewModel not initialized when attempting to set up adapter.")
//        }
//        itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback(viewModel, bookmarksAdapter, 0, ItemTouchHelper.LEFT))
        with(binding) {
            bookmarkRecyclerView.adapter = bookmarksAdapter
            bookmarkRecyclerView.addItemDecoration(DividerItemDecoration(requireContext(), RecyclerView.VERTICAL))
//            itemTouchHelper.attachToRecyclerView(bookmarkRecyclerView)
        }
    }

    private fun setupRefreshFab() {
        binding.setFabClickListener {
            if(NetworkUtils.isNetworkAvailable(requireContext())) {
                mainViewModel.refresh()
                getLocationAndWeather()
            } else {
                //show no network snack bar
                mainViewModel.showSnackBarMessage("현재 네트워크 연결이 없습니다.")
            }
        }
    }

    private fun getLocationAndWeather() {
        val context = context ?: return
        val fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        fusedLocationProviderClient.lastLocation.addOnSuccessListener {

            // 기본 사용자 위치는 서울 시청으로 설정
            var address: List<String> = listOf("대한민국", "서울특별시", "중구", "명동")

            if(it != null) {
                if(NetworkUtils.isNetworkAvailable(context)) {
                    val geocoder = Geocoder(context, Locale.KOREA)


                    try {
                        // 사용자 위치에 대해 최대 5개의 주소 정보 요청
                        val addrList = geocoder.getFromLocation(it.latitude, it.longitude, 5)

                        for (addr in addrList) {
                            val splitedAddr = addr.getAddressLine(0).split(" ")

                            // 나라, 도시, 구, 동 형태로 파싱된 주소 사용
                            if (splitedAddr[2].endsWith("구") && splitedAddr[3].endsWith("동")) {
                                address = splitedAddr
                                break
                            }
                        }
                        weatherViewModel.loadWeather(address[2], address[3])

                    } catch(e: IOException) {
                        // grpc 에러 나는 경우 그냥 패스
                        e.printStackTrace()
                        weatherViewModel.loadWeather(address[2], address[3])
                        Log.i("BookmarkFrag", "failed at getting user's location, " + weatherViewModel.loadSucceed.value)
                    }
                }
            }
            else {
                weatherViewModel.loadWeather(address[2], address[3])
                Log.i("BookmarkFrag", "failed at getting user's location, " + weatherViewModel.loadSucceed.value)
            }
        }
    }

    override fun onItemClick(station: StationRe) {
        mainViewModel.navigateToMapFrag(station.stationId)
    }

    private fun initSnackBar() {
        view?.setupSnackBar(this, mainViewModel.snackBarText, Snackbar.LENGTH_LONG)
    }

    override fun onResume() {
        super.onResume()
        (activity as AppCompatActivity).supportActionBar!!.show()
        (requireActivity()).appbar_title.text = resources.getText(R.string.title_bookmark_frag)
    }

    companion object {
        fun newInstance() = BookmarkFragment()
    }
}
