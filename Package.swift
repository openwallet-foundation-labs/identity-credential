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
         url: "https://apps.multipaz.org/xcf/Multipaz-0.93.0-pre.35.ffb1f3df.xcframework.zip",
         checksum:"1f578fdd49ad6de39e5f5077a684fe2008927836ca5aac4dfe8fd365ec6ec4e3")
   ]
)
