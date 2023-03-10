package com.android.mdl.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.GravityCompat
import androidx.navigation.Navigation
import androidx.navigation.findNavController
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.android.identity.OriginInfo
import com.android.identity.OriginInfoWebsite
import com.android.mdl.app.databinding.ActivityMainBinding
import com.android.mdl.app.fragment.TransferDocumentFragment
import com.android.mdl.app.util.log
import com.android.mdl.app.util.logError
import com.android.mdl.app.util.logInfo
import com.android.mdl.app.util.logWarning
import com.android.mdl.app.viewmodel.ShareDocumentViewModel
import com.google.android.material.elevation.SurfaceColors
import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

class MainActivity : AppCompatActivity() {

    private val viewModel: ShareDocumentViewModel by viewModels()
    private lateinit var binding: ActivityMainBinding
    private lateinit var pendingIntent: PendingIntent
    private var nfcAdapter: NfcAdapter? = null

    private val navController by lazy {
        Navigation.findNavController(this, R.id.nav_host_fragment)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val color = SurfaceColors.SURFACE_2.getColor(this)
        window.statusBarColor = color
        window.navigationBarColor = color
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupDrawerLayout()
        setupNfc()
        onNewIntent(intent)

        Security.addProvider(BouncyCastleProvider())
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        // Create a generic PendingIntent that will be deliver to this activity. The NFC stack
        // will fill in the intent with the details of the discovered tag before delivering to
        // this activity.
        val intent = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setupDrawerLayout() {
        binding.nvSideDrawer.setupWithNavController(navController)
        setupActionBarWithNavController(this, navController, binding.dlMainDrawer)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        log("New intent on Activity $intent")

        if (intent == null) {
            return
        }

        var mdocUri: String? = null
        var mdocReferrerUri: String? = null
        if (intent.scheme.equals("mdoc")) {
            val uri = Uri.parse(intent.toUri(0))
            mdocUri = "mdoc://" + uri.authority

            mdocReferrerUri = intent.extras?.get(Intent.EXTRA_REFERRER)?.toString()
        }

        if (mdocUri == null) {
            logError("No mdoc:// URI")
            return
        }
        logInfo("uri: $mdocUri")

        val originInfos = ArrayList<OriginInfo>()
        if (mdocReferrerUri == null) {
            logWarning("No referrer URI")
            // TODO: maybe bail in the future if this isn't set.
        } else {
            logInfo("referrer: $mdocReferrerUri")
            originInfos.add(OriginInfoWebsite(1, mdocReferrerUri))
        }

        viewModel.startPresentationReverseEngagement(mdocUri, originInfos)
        val navController = findNavController(R.id.nav_host_fragment)
        navController.navigate(
            R.id.transferDocumentFragment,
            bundleOf(TransferDocumentFragment.CLOSE_AFTER_SERVING_KEY to true)
        )
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, binding.dlMainDrawer)
    }

    override fun onBackPressed() {
        if (binding.dlMainDrawer.isDrawerOpen(GravityCompat.START)) {
            binding.dlMainDrawer.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}