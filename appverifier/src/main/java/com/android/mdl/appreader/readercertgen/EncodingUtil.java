package com.android.mdl.appreader.readercertgen;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class EncodingUtil {

	private static final SimpleDateFormat SHORT_ISO_DATEFORMAT = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

	private EncodingUtil() {
		// TODO Auto-generated constructor stub
	}

	static Date parseShortISODate(String date) {
		try {
			return SHORT_ISO_DATEFORMAT.parse(date);
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
	}

}
