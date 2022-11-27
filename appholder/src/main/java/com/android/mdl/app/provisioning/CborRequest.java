package com.android.mdl.app.provisioning;

import static com.android.mdl.app.util.LogginExtensionsKt.log;
import static com.android.mdl.app.util.LogginExtensionsKt.logError;

import androidx.annotation.Nullable;

import com.android.mdl.app.util.FormatUtil;
import com.android.volley.NetworkResponse;
import com.android.volley.ParseError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.Response.ErrorListener;
import com.android.volley.Response.Listener;
import com.android.volley.toolbox.HttpHeaderParser;

import java.io.ByteArrayInputStream;
import java.util.List;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.MajorType;

class CborRequest extends Request<DataItem> {
    private final Listener<DataItem> listener;

    /**
     * Creates a new request with the given method.
     *
     * @param method        the request {@link Method} to use
     * @param url           URL to fetch the string at
     * @param listener      Listener to receive the String response
     * @param errorListener Error listener, or null to ignore errors
     */
    public CborRequest(
            int method,
            String url,
            Listener<DataItem> listener,
            @Nullable ErrorListener errorListener) {
        super(method, url, errorListener);
        this.listener = listener;
    }

    @Override
    protected void deliverResponse(DataItem response) {
        listener.onResponse(response);
    }

    @Override
    protected Response<DataItem> parseNetworkResponse(NetworkResponse response) {
        log(this, "response.data", null);
        FormatUtil.INSTANCE.debugPrintEncodeToString(response.data);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(response.data);
        try {
            List<DataItem> dataItems = new CborDecoder(inputStream).decode();
            if (dataItems.size() != 1) {
                String message = "Cbor decode error expected 1 found " + dataItems.size();
                logError(this, message);
                throw new CborException(message);
            }
            DataItem dataItem = dataItems.get(0);
            if (dataItem.getMajorType() != MajorType.MAP) {
                String message = "Cbor decode error Map expected found " + dataItem.getMajorType();
                logError(this, message);
                throw new CborException(message);
            }

            return Response.success(dataItem,
                    HttpHeaderParser.parseCacheHeaders(response));
        } catch (CborException e) {
            // This should never happen so just adding to Log.
            String message = "Error decoding CBOR " + e.getMessage() + " response " + response;
            log(this, message, e.fillInStackTrace());
            return Response.error(new ParseError(e));
        }
    }
}
