package javacardx.biometry;

public final class BioBuilder {
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

  BioBuilder() {
  }

  public static OwnerBioTemplate buildBioTemplate(byte var0, byte var1) throws BioException {
    return null;
  }

  public static OwnerBioTemplate buildBioTemplate(byte var0, byte var1, byte[] var2, byte var3) throws BioException {
    return null;
  }
}

