LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_EMMA_COVERAGE_FILTER := +com.android.calendar.*

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under,src)

# bundled
LOCAL_STATIC_JAVA_LIBRARIES += android-common

# unbundled
#LOCAL_STATIC_JAVA_LIBRARIES := android-common
#LOCAL_SDK_VERSION := current

LOCAL_PACKAGE_NAME := Calendar

LOCAL_PROGUARD_FLAG_FILES := proguard.flags

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
