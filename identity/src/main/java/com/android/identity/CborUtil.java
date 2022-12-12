package com.android.identity;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.lang.annotation.Retention;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.CborException;
import co.nstant.in.cbor.model.AbstractFloat;
import co.nstant.in.cbor.model.ByteString;
import co.nstant.in.cbor.model.DataItem;
import co.nstant.in.cbor.model.DoublePrecisionFloat;
import co.nstant.in.cbor.model.NegativeInteger;
import co.nstant.in.cbor.model.SimpleValue;
import co.nstant.in.cbor.model.UnsignedInteger;

class CborUtil {
    private static final String TAG = "CborUtil";


    static final int DIAGNOSTICS_FLAG_EMBEDDED_CBOR = (1 << 0);

    static final int DIAGNOSTICS_FLAG_PRETTY_PRINT = (1 << 1);

    @Retention(SOURCE)
    @IntDef(
            flag = true,
            value = {
                    DIAGNOSTICS_FLAG_EMBEDDED_CBOR,
                    DIAGNOSTICS_FLAG_PRETTY_PRINT
            })
    public @interface DiagnosticsFlags {
    }

    // Returns true iff all elements in |items| are not compound (e.g. an array or a map).
    private static boolean cborAreAllDataItemsNonCompound(@NonNull List<DataItem> items,
                                                          @DiagnosticsFlags int flags) {
        for (DataItem item : items) {
            if ((flags & CborUtil.DIAGNOSTICS_FLAG_EMBEDDED_CBOR) != 0
                    && item.hasTag() && item.getTag().getValue() == 24) {
                return false;
            }
            switch (item.getMajorType()) {
                case ARRAY:
                case MAP:
                    return false;

                default:
                    // Do nothing
                    break;
            }
        }
        return true;
    }

    private static boolean cborFitsInSingleLine(@NonNull List<DataItem> items,
                                                @DiagnosticsFlags int flags) {
        // For now just use this heuristic.
        return cborAreAllDataItemsNonCompound(items, flags) && items.size() < 8;
    }

    private static void toDiagnostics(@NonNull StringBuilder sb,
                                      int indent,
                                      @NonNull DataItem dataItem,
                                      @DiagnosticsFlags int flags) {
        int count;

        boolean pretty = ((flags & DIAGNOSTICS_FLAG_PRETTY_PRINT) != 0);
        String indentString = "";
        if (pretty) {
            StringBuilder indentBuilder = new StringBuilder();
            for (int n = 0; n < indent; n++) {
                indentBuilder.append(' ');
            }
            indentString = indentBuilder.toString();
        }

        if (dataItem.hasTag()) {
            sb.append(String.format(Locale.US, "%d(", dataItem.getTag().getValue()));
        }

        switch (dataItem.getMajorType()) {
            case INVALID:
                sb.append("<invalid>");
                break;

            case UNSIGNED_INTEGER:
                // Major type 0: an unsigned integer.
                sb.append(((UnsignedInteger) dataItem).getValue());
                break;

            case NEGATIVE_INTEGER:
                // Major type 1: a negative integer.
                sb.append(((NegativeInteger) dataItem).getValue());
                break;

            case BYTE_STRING:
                // Major type 2: a byte string.
                byte[] bstrValue = ((ByteString) dataItem).getBytes();

                if (dataItem.hasTag() && dataItem.getTag().getValue() == 24
                        && (flags & DIAGNOSTICS_FLAG_EMBEDDED_CBOR) != 0) {
                    sb.append("<< ");
                    ByteArrayInputStream bais = new ByteArrayInputStream(bstrValue);
                    List<DataItem> dataItems = null;
                    try {
                        dataItems = new CborDecoder(bais).decode();
                        if (dataItems.size() >= 1) {
                            toDiagnostics(sb, indent, Util.cborDecode(bstrValue), flags);
                            if (dataItems.size() > 1) {
                                Logger.w(TAG, "Multiple data items in embedded CBOR, "
                                        + "only printing the first");
                                sb.append(String.format(Locale.US,
                                        " Error: omitting %d additional items",
                                        dataItems.size() - 1));
                            }
                        } else {
                            sb.append("Error: 0 Data Items");
                        }
                    } catch (CborException e) {
                        // Never throw an exception
                        sb.append("Error Decoding CBOR");
                        Logger.w(TAG, "Error decoding CBOR: " + e);
                    }
                    sb.append(" >>");
                } else {
                    sb.append("h'");
                    count = 0;
                    for (byte b : bstrValue) {
                        sb.append(String.format(Locale.US, "%02x", b));
                        count++;
                    }
                    sb.append("'");
                }
                break;

            case UNICODE_STRING:
                // Major type 3: string of Unicode characters that is encoded as UTF-8 [RFC3629].
                String strValue = Util.checkedStringValue(dataItem);
                String escapedStrValue = strValue.replace("\"", "\\\"");
                sb.append("\"" + escapedStrValue + "\"");
                break;

            case ARRAY:
                // Major type 4: an array of data items.
                List<DataItem> items = ((co.nstant.in.cbor.model.Array) dataItem).getDataItems();
                if (!pretty || cborFitsInSingleLine(items, flags)) {
                    sb.append("[");
                    count = 0;
                    for (DataItem item : items) {
                        toDiagnostics(sb, indent, item, flags);
                        if (++count < items.size()) {
                            sb.append(", ");
                        }
                    }
                    sb.append("]");
                } else {
                    sb.append("[\n").append(indentString);
                    count = 0;
                    for (DataItem item : items) {
                        sb.append("  ");
                        toDiagnostics(sb, indent + 2, item, flags);
                        if (++count < items.size()) {
                            sb.append(",");
                        }
                        sb.append("\n").append(indentString);
                    }
                    sb.append("]");
                }
                break;

            case MAP:
                // Major type 5: a map of pairs of data items.
                Collection<DataItem> keys = ((co.nstant.in.cbor.model.Map) dataItem).getKeys();
                if (!pretty || keys.size() == 0 ){
                    sb.append("{");
                    count = 0;
                    for (DataItem key : keys) {
                        DataItem value = ((co.nstant.in.cbor.model.Map) dataItem).get(key);
                        toDiagnostics(sb, indent, key, flags);
                        sb.append(": ");
                        toDiagnostics(sb, indent + 2, value, flags);
                        if (++count < keys.size()) {
                            sb.append(", ");
                        }
                    }
                    sb.append("}");
                } else {
                    sb.append("{\n").append(indentString);
                    count = 0;
                    for (DataItem key : keys) {
                        sb.append("  ");
                        DataItem value = ((co.nstant.in.cbor.model.Map) dataItem).get(key);
                        toDiagnostics(sb, indent + 2, key, flags);
                        sb.append(": ");
                        toDiagnostics(sb, indent + 2, value, flags);
                        if (++count < keys.size()) {
                            sb.append(",");
                        }
                        sb.append("\n").append(indentString);
                    }
                    sb.append("}");
                }
                break;

            case TAG:
                // Major type 6: optional semantic tagging of other major types
                //
                // We never encounter this one since it's automatically handled via the
                // DataItem that is tagged.
                break;

            case SPECIAL:
                // Major type 7: floating point numbers and simple data types that need no
                // content, as well as the "break" stop code.
                if (dataItem instanceof SimpleValue) {
                    switch (((SimpleValue) dataItem).getSimpleValueType()) {
                        case FALSE:
                            sb.append("false");
                            break;
                        case TRUE:
                            sb.append("true");
                            break;
                        case NULL:
                            sb.append("null");
                            break;
                        case UNDEFINED:
                            sb.append("undefined");
                            break;
                        case RESERVED:
                            sb.append("reserved");
                            break;
                        case UNALLOCATED:
                            sb.append("simple(");
                            sb.append(((SimpleValue) dataItem).getValue());
                            sb.append(")");
                            break;
                    }
                } else if (dataItem instanceof DoublePrecisionFloat) {
                    DecimalFormat df = new DecimalFormat("0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                    df.setMaximumFractionDigits(340);
                    sb.append(df.format(((DoublePrecisionFloat) dataItem).getValue()));
                } else if (dataItem instanceof AbstractFloat) {
                    DecimalFormat df = new DecimalFormat("0",
                            DecimalFormatSymbols.getInstance(Locale.ENGLISH));
                    df.setMaximumFractionDigits(340);
                    sb.append(df.format(((AbstractFloat) dataItem).getValue()));
                } else {
                    sb.append("break");
                }
                break;
        }

        if (dataItem.hasTag()) {
            sb.append(")");
        }

    }

    static @NonNull
    String toDiagnostics(@NonNull DataItem dataItem) {
        return toDiagnostics(dataItem, 0);
    }

    static @NonNull
    String toDiagnostics(@NonNull DataItem dataItem, @DiagnosticsFlags int flags) {
        StringBuilder sb = new StringBuilder();
        toDiagnostics(sb, 0, dataItem, flags);
        return sb.toString();
    }

    static @NonNull
    String toDiagnostics(@NonNull byte[] encodedCbor) {
        return toDiagnostics(encodedCbor, 0);
    }

    static @NonNull
    String toDiagnostics(@NonNull byte[] encodedCbor, @DiagnosticsFlags int flags) {
        StringBuilder sb = new StringBuilder();

        ByteArrayInputStream bais = new ByteArrayInputStream(encodedCbor);
        List<DataItem> dataItems = null;
        try {
            dataItems = new CborDecoder(bais).decode();
        } catch (CborException e) {
            // Never throw an exception
            return "Error Decoding CBOR";
        }
        int count = 0;
        for (DataItem dataItem : dataItems) {
            if (count > 0) {
                sb.append(",\n");
            }
            toDiagnostics(sb, 0, dataItem, flags);
            count++;
        }
        return sb.toString();
    }

}
