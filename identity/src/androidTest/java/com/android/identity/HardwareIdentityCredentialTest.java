package com.android.identity;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HardwareIdentityCredentialTest {

  @Test
  public void convertFromAndroidResultDataStatus() {
    checkConvertStatus(ResultData.STATUS_NOT_IN_REQUEST_MESSAGE, android.security.identity.ResultData.STATUS_NOT_IN_REQUEST_MESSAGE);
    checkConvertStatus(ResultData.STATUS_NOT_REQUESTED, android.security.identity.ResultData.STATUS_NOT_REQUESTED);
    checkConvertStatus(ResultData.STATUS_NO_ACCESS_CONTROL_PROFILES, android.security.identity.ResultData.STATUS_NO_ACCESS_CONTROL_PROFILES);
    checkConvertStatus(ResultData.STATUS_NO_SUCH_ENTRY, android.security.identity.ResultData.STATUS_NO_SUCH_ENTRY);
    checkConvertStatus(ResultData.STATUS_OK, android.security.identity.ResultData.STATUS_OK);
    checkConvertStatus(ResultData.STATUS_READER_AUTHENTICATION_FAILED, android.security.identity.ResultData.STATUS_READER_AUTHENTICATION_FAILED);
    checkConvertStatus(ResultData.STATUS_USER_AUTHENTICATION_FAILED, android.security.identity.ResultData.STATUS_USER_AUTHENTICATION_FAILED);
  }

  private static void checkConvertStatus(@ResultData.Status int expected, int androidStatus) {
    assertEquals(expected, HardwareIdentityCredential.convertFromAndroidStatus(androidStatus));
  }
}
