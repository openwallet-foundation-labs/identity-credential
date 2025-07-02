project 'samples/testapp/iosApp/TestApp.xcodeproj/'

platform :ios, '16.0'

target 'TestApp' do
  use_frameworks!
  pod 'GoogleMLKit/BarcodeScanning', '8.0.0'
  pod 'GoogleMLKit/FaceDetection', '8.0.0'
  pod 'TensorFlowLiteObjC', :subspecs => ['CoreML', 'Metal']

  post_install do |installer|
    installer.pods_project.targets.each do |target|
      target.build_configurations.each do |config|
        config.build_settings["EXCLUDED_ARCHS[sdk=iphonesimulator*]"] = "arm64"
      end
    end
  end
end
