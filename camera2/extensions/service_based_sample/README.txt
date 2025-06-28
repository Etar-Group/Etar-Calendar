Service-Based reference implementation
This reference implementation demonstrates how to implement the Extensions in a standalone service.

It contains the following components:
1) oem_library:
   A Camera Extensions OEM library that implements the Extensions-Interface to enable both Camera2
   and CameraX Extensions APIS. It is basically a pass-through that forwards all calls from
   Extensions-Interface to the service. If it works well for you, you don't have to modify it.

   It also contains the AIDL and wrapper classes for communicating with the service. AIDL and
   wrapper classes are located in androidx.camera.extensions.impl.service package.

   Both Advanced Extender and Basic Extender is supported, however, Advanced Extender is enabled
   by default. If you want to use Basic Extender to implement, change
   ExtensionsVersionImpl#isAdvancedExtenderImplemented to return false.

2) extensions_service:
   A sample implementation of extensions service is provided. You should add your real implementation
   here. The sample service is built using Android.bp, but you can transform it into a gradle
   project by adding the stub jar of oem_library (located in
   out/target/common/obj/JAVA_LIBRARIES/service_based_camera_extensions_intermediates/) to the
   dependencies.

In this service-based architecture, all functionalities of the Extensions-Interface are supposed to
be implemented in extensions_service except for ExtensionVersionImpl#checkApiVersion and
#isAdvancedExtenderImplemented which require you to implement it in the oem_library.