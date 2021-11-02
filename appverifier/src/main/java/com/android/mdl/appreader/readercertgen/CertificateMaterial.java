package com.android.mdl.appreader.readercertgen;

import java.math.BigInteger;
import java.util.Date;
import java.util.Optional;

public interface CertificateMaterial {
    int PATHLENGTH_NOT_A_CA = -1;

    BigInteger serialNumber();

    Date startDate();

    Date endDate();

    int keyUsage();

    Optional<String> extendedKeyUsage();

    int pathLengthConstraint();
}	