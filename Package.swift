// swift-tools-version:5.3
import PackageDescription

let package = Package(
   name: "Multipaz",
   platforms: [
     .iOS(.v14),
   ],
   products: [
      .library(name: "Multipaz", targets: ["Multipaz"])
   ],
   targets: [
      .binaryTarget(
         name: "Multipaz",
         url: "https://apps.multipaz.org/xcf/Multipaz-0.92.0-pre.14.f18516c5.xcframework.zip",
         checksum:"ebfd0bdc79d6666fd4d380033a47df903a1851008a0795379a954703aa0c1ac2")
   ]
)
