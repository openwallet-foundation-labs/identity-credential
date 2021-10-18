package androidx.security.identity;

import android.content.Context;
import android.nfc.NdefRecord;
import android.util.Pair;
import androidx.annotation.NonNull;
import androidx.security.identity.Constants.LoggingFlag;
import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.builder.ArrayBuilder;
import java.util.List;

/**
 * An object used to specify how an mdoc reader can connect to an mdoc.
 *
 * <p>In ISO/IEC 18013-5 the mdoc reader obtains a list of these out-of-band (either QR code and
 * parsing the <code>DeviceEngagement</code> CBOR or via NFC handover) and each represent a way
 * to connect to the mdoc.
 *
 * <p>For some device retrieval methods, a single entry in <code>DeviceRetrievalMethod</code> CBOR
 * may result in multiple instances of this object, for example for BLE it's possible for an
 * mdoc to convey that it supports both mdoc central client mode and mdoc peripheral server mode.
 *
 * <p>A list of these objects are returned in
 * {@link VerificationHelper.Listener#onDeviceEngagementReceived(List)} allowing the verifier
 * application to select which address to connect to using the
 * {@link VerificationHelper#connect(DataRetrievalAddress)} address.
 *
 * <p>There is currently no API on this class which enables the verifier application to
 * disambiguate between the various methods. For now the application can use {@link #toString()}
 * to show a list to the user to do this. A proper API will be added in the future.
 */
abstract public class DataRetrievalAddress {

  abstract @NonNull DataTransport getDataTransport(
      @NonNull Context context, @LoggingFlag int loggingFlags);

  abstract void addDeviceRetrievalMethodsEntry(ArrayBuilder<CborBuilder> arrayBuilder,
      List<DataRetrievalAddress> listeningAddresses);

  abstract Pair<NdefRecord, byte[]> createNdefRecords(List<DataRetrievalAddress> listeningAddresses);

}
