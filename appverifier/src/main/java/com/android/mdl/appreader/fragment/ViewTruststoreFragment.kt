package com.android.mdl.appreader.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import com.android.mdl.appreader.theme.ReaderAppTheme
import com.android.mdl.appreader.util.KeysAndCertificates
import java.security.cert.X509Certificate
import java.time.Instant

class ViewTruststoreFragment : Fragment() {
  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?,
  ): View {
    val trustedCertificates = KeysAndCertificates.getTrustedIssuerCertificates(requireContext())
    return ComposeView(requireContext()).apply {
      setContent {
        ReaderAppTheme {
          CertificatesList(trustedCertificates)
        }
      }
    }
  }
}

@Composable
private fun CertificatesList(certificates: List<X509Certificate>) {
  val now = Instant.now()
  val padding = Modifier.padding(horizontal = 2.dp)
  val colorRegular = MaterialTheme.colorScheme.onBackground
  val colorError = MaterialTheme.colorScheme.error

  Column(modifier = Modifier.verticalScroll(state = rememberScrollState())) {
    certificates.forEach { certificate ->
      OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = certificate.subjectDN.toString(),
          style = MaterialTheme.typography.bodyMedium,
          color = colorRegular,
          modifier = padding
        )
        Text(
          text = "Not before: " + certificate.notBefore.toString(),
          style = MaterialTheme.typography.bodyMedium,
          color = if (now > certificate.notBefore.toInstant()) colorRegular else colorError,
          modifier = padding
        )
        Text(
          text = "Not after: " + certificate.notAfter.toString(),
          style = MaterialTheme.typography.bodyMedium,
          color = if (now < certificate.notAfter.toInstant()) colorRegular else colorError,
          modifier = padding
        )
      }
    }
  }
}
