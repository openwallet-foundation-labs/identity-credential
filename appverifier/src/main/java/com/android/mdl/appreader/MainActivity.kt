package com.android.mdl.appreader

import android.app.ComponentCaller
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.navigation.Navigation
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupWithNavController
import com.android.mdl.appreader.databinding.ActivityMainBinding
import com.android.mdl.appreader.util.logDebug
import com.google.android.material.elevation.SurfaceColors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var mAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null
    private val navController by lazy {
        Navigation.findNavController(this, R.id.nav_host_fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: b/393388152 - statusBarColor and navigationBarColor are deprecated, new impl. needed for compatibility.
        val color = SurfaceColors.SURFACE_2.getColor(this)
        @Suppress("DEPRECATION")
        window.statusBarColor = color
        @Suppress("DEPRECATION")
        window.navigationBarColor = color

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDrawerLayout()
        onBackPressedDispatcher.addCallback(this) {
            if (binding.dlMainDrawer.isDrawerOpen(GravityCompat.START)) {
                binding.dlMainDrawer.closeDrawer(GravityCompat.START)
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }

        mAdapter = NfcAdapter.getDefaultAdapter(this)
        // Create a generic PendingIntent that will be deliver to this activity. The NFC stack
        // will fill in the intent with the details of the discovered tag before delivering to
        // this activity.
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        mPendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setupDrawerLayout() {
        binding.nvSideDrawer.setupWithNavController(navController)
        NavigationUI.setupActionBarWithNavController(this, navController, binding.dlMainDrawer)
    }

    override fun onResume() {
        super.onResume()
        mAdapter?.enableForegroundDispatch(this, mPendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        mAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        logDebug("New intent on Activity $intent")
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, binding.dlMainDrawer)
    }
}