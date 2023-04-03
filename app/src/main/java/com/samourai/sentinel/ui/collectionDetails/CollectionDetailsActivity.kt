package com.samourai.sentinel.ui.collectionDetails

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.samourai.sentinel.R
import com.samourai.sentinel.data.PubKeyCollection
import com.samourai.sentinel.data.repository.CollectionRepository
import com.samourai.sentinel.databinding.ActivityCollectionDetailsBinding
import com.samourai.sentinel.ui.SentinelActivity
import com.samourai.sentinel.ui.collectionDetails.receive.ReceiveFragment
import com.samourai.sentinel.ui.collectionDetails.send.SendFragment
import com.samourai.sentinel.ui.collectionDetails.transactions.TransactionsFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject


class CollectionDetailsActivity : SentinelActivity() {


    private lateinit var pagerAdapter: PagerAdapter
    private val receiveFragment: ReceiveFragment = ReceiveFragment()
    private val sendFragment: SendFragment = SendFragment()
    private val transactionsFragment: TransactionsFragment = TransactionsFragment()
    private var collection: PubKeyCollection? = null
    private val repository: CollectionRepository by inject(CollectionRepository::class.java)
    private lateinit var binding: ActivityCollectionDetailsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCollectionDetailsBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        pagerAdapter = PagerAdapter(this)
        binding.fragmentHostContainerPager.adapter = pagerAdapter
        binding.fragmentHostContainerPager.isUserInputEnabled = false

        checkIntent()
        val receiveViewModel: CollectionDetailsViewModel by viewModels(factoryProducer = {
            CollectionDetailsViewModel.getFactory(
                    collection!!
            )
        })
        receiveViewModel.getCollections().observe(this, Observer {
            intent.extras?.getString("collection")?.let { it1 ->
                receiveViewModel.getRepository().findById(it1)?.let {
                    collection = it
                }
            }
            if (collection != null) {
                receiveFragment.setCollection(collection!!)
                sendFragment.setCollection(collection!!)
                transactionsFragment.initViewModel(collection!!)
            } else {
                finish()
            }
        })


        transactionsFragment.setBalance(receiveViewModel.getBalance())

        receiveViewModel.getFiatBalance().observe(this) {
            transactionsFragment.setBalanceFiat(receiveViewModel.getFiatBalance())
        }

        binding.bottomNav.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.bottom_nav_receive -> {
                    binding.fragmentHostContainerPager.setCurrentItem(0, true)
                }
                R.id.bottom_nav_send -> {
                    binding.fragmentHostContainerPager.setCurrentItem(2, true)
                }
                R.id.bottom_nav_transaction -> {
                    binding.fragmentHostContainerPager.setCurrentItem(1, true)
                }
            }
            true
        }

        binding.fragmentHostContainerPager.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {

            override fun onPageSelected(position: Int) {
                invalidateOptionsMenu()
                when (position) {
                    0 -> {
                        binding.bottomNav.selectedItemId = R.id.bottom_nav_receive
                    }
                    1 -> {
                        binding.bottomNav.selectedItemId = R.id.bottom_nav_transaction
                    }
                    else -> {
                        binding.bottomNav.selectedItemId = R.id.bottom_nav_send
                    }
                }
            }

        })

        binding.fragmentHostContainerPager.visibility = View.INVISIBLE
        GlobalScope.launch(Dispatchers.Main) {
            delay(1)
            binding.fragmentHostContainerPager.setCurrentItem(1, false)
            binding.fragmentHostContainerPager.visibility = View.VISIBLE
        }

        val pubIndexObserver = Observer<Int> { newIndex ->
            receiveFragment.setDropDownPub(newIndex)
            sendFragment.setDropDownPub(newIndex)
        }

        transactionsFragment.indexPubSelected.observe(this, pubIndexObserver)
    }


    override fun onBackPressed() {
        if (sendFragment.isVisible) {
            if (sendFragment.onBackPressed()) {
                super.onBackPressed()
            }
        } else {
            super.onBackPressed()
        }
    }

    private fun checkIntent() {
        if (intent.extras != null) {
            if (intent.extras!!.containsKey("collection")) {
                repository.findById(intent.extras!!.getString("collection")!!)?.let {
                    collection = it
                }
                if (collection != null) {
                    receiveFragment.setCollection(collection!!)
                    sendFragment.setCollection(collection!!)
                    transactionsFragment.initViewModel(collection!!)
                } else {
                    finish()
                }
            } else {
                finish()
                return
            }
        } else {
            finish()
            return
        }

    }

    private inner class PagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> {
                    receiveFragment
                }
                1 -> {
                    transactionsFragment
                }
                else -> {
                    sendFragment
                }
            }
        }
    }


}

