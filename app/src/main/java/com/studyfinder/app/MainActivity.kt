package com.studyfinder.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.firebase.auth.FirebaseAuth
import com.studyfinder.app.databinding.ActivityMainBinding
import com.studyfinder.app.util.DataSeeder

/**
 * The single Activity for the whole app.
 *
 * It owns exactly two things: the NavHostFragment, and the bottom navigation
 * bar's visibility. Screen logic lives in fragments; the start destination is
 * resolved by [com.studyfinder.app.ui.auth.SplashFragment], not here.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    private val authStateListener = FirebaseAuth.AuthStateListener {
        /*
        if (it.currentUser != null && BuildConfig.DEBUG) {
            DataSeeder.seedAll(this)
        }
        */
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
    }

    private companion object {
        /** Auth, detail, create and manage screens are full-bleed. */
        val TOP_LEVEL_DESTINATIONS = setOf(
            R.id.homeFragment,
            R.id.mySessionsFragment,
            R.id.inboxFragment,
            R.id.profileFragment,
        )
    }
}
