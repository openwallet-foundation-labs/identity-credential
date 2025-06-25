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
         url: "https://apps.multipaz.org/xcf/Multipaz-0.92.0.xcframework.zip",
         checksum:"826337fd0c70cd3e216d967ee48b3ca0b15c5abe7ca555aad0a777a2bbb11c0e")
   ]
)
