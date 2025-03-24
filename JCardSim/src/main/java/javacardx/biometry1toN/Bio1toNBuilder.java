package javacardx.biometry1toN;

public class Bio1toNBuilder {
  public static final byte FACIAL_FEATURE = 1;
  public static final byte VOICE_PRINT = 2;
  public static final byte FINGERPRINT = 3;
  public static final byte IRIS_SCAN = 4;
  public static final byte RETINA_SCAN = 5;
  public static final byte HAND_GEOMETRY = 6;
  public static final byte SIGNATURE = 7;
  public static final byte KEYSTROKES = 8;
  public static final byte LIP_MOVEMENT = 9;
  public static final byte THERMAL_FACE = 10;
  public static final byte THERMAL_HAND = 11;
  public static final byte GAIT_STYLE = 12;
  public static final byte BODY_ODOR = 13;
  public static final byte DNA_SCAN = 14;
  public static final byte EAR_GEOMETRY = 15;
  public static final byte FINGER_GEOMETRY = 16;
  public static final byte PALM_GEOMETRY = 17;
  public static final byte VEIN_PATTERN = 18;
  public static final byte PASSWORD = 31;
  public static final byte DEFAULT_INITPARAM = 0;

  Bio1toNBuilder() {
  }

  public static OwnerBioMatcher buildBioMatcher(short var0, byte var1, byte var2) throws Bio1toNException {
    return null;
  }

  public static OwnerBioMatcher buildBioMatcher(short var0, byte var1, byte var2, byte[] var3, byte var4) throws Bio1toNException {
    return null;
  }

  public static OwnerBioTemplateData buildBioTemplateData(byte var0) throws Bio1toNException {
    return null;
  }

  public static OwnerBioTemplateData buildBioTemplateData(byte var0, byte[] var1) throws Bio1toNException {
    return null;
  }
}

