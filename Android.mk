LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := user

LOCAL_SRC_FILES := $(call all-subdir-java-files)

# TODO: Remove dependency of application on the test runner (android.test.runner) 
# library.
LOCAL_JAVA_LIBRARIES := android.test.runner

LOCAL_PACKAGE_NAME := Calendar

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
