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
         url: "https://apps.multipaz.org/xcf/Multipaz-0.92.1.xcframework.zip",
         checksum:"07a728870baf73ed3111a2f8a50f3e7b83f1625b76343b0a9f7b5152cab47680")
   ]
)
