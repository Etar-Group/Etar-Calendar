LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under,src)

#LOCAL_SDK_VERSION := current

# TODO: Remove dependency of application on the test runner (android.test.runner)
# library.
LOCAL_JAVA_LIBRARIES := android.test.runner

# TODO Remove this once LaunchActivity is cleaned up
LOCAL_STATIC_JAVA_LIBRARIES := gsf-client

LOCAL_STATIC_JAVA_LIBRARIES += android-common
LOCAL_STATIC_JAVA_LIBRARIES += calendar # Shared calendar code

LOCAL_PACKAGE_NAME := Calendar

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
