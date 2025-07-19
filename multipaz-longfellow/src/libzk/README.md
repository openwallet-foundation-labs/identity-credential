This files contains instructions for how to build the
[Longfellow ZK library](https://github.com/google/longfellow-zk) for the
various platforms that the multipaz-longfellow KMP library is available on.

# Platforms using Java

For platforms using Java, JNI is used and the the glue code used is:
- `org_multipaz_mdoc_zkp_longfellow_LongfellowNatives.cc`
- `org_multipaz_mdoc_zkp_longfellow_LongfellowNatives.h`

The binary `libzkp` artifacts in `androidMain` and `jvmMain` are compiled
using those source files and the source from Longfellow ZK library. We
plan to provide instructions on how to compile these files.

# iOS

To be written.
