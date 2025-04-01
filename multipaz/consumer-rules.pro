# Prevent BouncyCastle BC provider and EC algorithm from being stripped out
-keep class org.bouncycastle.jcajce.provider.keystore.BC$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.EC$Mappings { *; }
-keep class org.bouncycastle.jcajce.provider.keystore.bc.* { *; }
-keep class org.bouncycastle.jcajce.provider.asymmetric.ec.* { *; }
