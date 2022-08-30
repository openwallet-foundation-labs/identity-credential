package com.android.mdl.appreader.certutil;

import java.math.BigInteger;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface CertificateMaterial {	
	BigInteger serialNumber();
	Date startDate();
	Date endDate();
	int keyUsage();
	
	default List<String> extendedKeyUsage() {
	    return Collections.emptyList();
	}
	
    default boolean extendedKeyUsageCritical() {
        return true;
    }

    boolean isCa();
    
    Optional<Integer> pathLengthConstraint();
    
    default boolean includeKeyIds() {
        return true;
    }
    
    default List<String> criticalPolicyOids() {
        return Collections.emptyList();
    }
    
}	