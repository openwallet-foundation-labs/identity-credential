package com.android.mdl.app

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.util.LogPrinter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.android.identity.OriginInfo
import com.android.identity.OriginInfoWebsite
import com.android.mdl.app.fragment.ReverseEngagementFragmentDirections
import com.android.mdl.app.fragment.TransferDocumentFragment
import com.android.mdl.app.viewmodel.ShareDocumentViewModel

class MainActivity : AppCompatActivity() {

    companion object {
        private const val LOG_TAG = "MainActivity"
    }

    private lateinit var appBarConfiguration: AppBarConfiguration
    private var mAdapter: NfcAdapter? = null
    private var mPendingIntent: PendingIntent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val navController = findNavController(R.id.nav_host_fragment)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.selectDocumentFragment,
                R.id.transferDocumentFragment,
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)

        mAdapter = NfcAdapter.getDefaultAdapter(this)
        // Create a generic PendingIntent that will be deliver to this activity. The NFC stack
        // will fill in the intent with the details of the discovered tag before delivering to
        // this activity.
        val i = Intent(this, javaClass).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        mPendingIntent = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE)
    }

    override fun onResume() {
        super.onResume()
        mAdapter?.enableForegroundDispatch(this, mPendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        mAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(LOG_TAG, "New intent on Activity $intent")

        if (intent == null) {
            return;
        }

        var mdocUri : String? = null
        var mdocReferrerUri : String? = null
        if (intent.scheme.equals("mdoc")) {
            val uri = Uri.parse(intent.toUri(0))
            mdocUri = "mdoc://" + uri.authority

            mdocReferrerUri = intent.extras?.get(Intent.EXTRA_REFERRER)?.toString()
        }

        if (mdocUri == null) {
            Log.e(LOG_TAG, "No mdoc:// URI")
            return
        }
        if (mdocReferrerUri == null) {
            Log.e(LOG_TAG, "No referrer URI")
            return
        }

        Log.i(LOG_TAG, "uri: $mdocUri")
        Log.i(LOG_TAG, "referrer: $mdocReferrerUri")

        val originInfos = ArrayList<OriginInfo>()
        originInfos.add(OriginInfoWebsite(1, mdocReferrerUri))
        val vm = ViewModelProvider(this).get(ShareDocumentViewModel::class.java)
        vm.startPresentationReverseEngagement(mdocUri, originInfos)
        val navController = findNavController(R.id.nav_host_fragment)
        navController.navigate(R.id.transferDocumentFragment)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}