LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

# Include res dir from chips
chips_dir := ../../../frameworks/ex/chips/res
res_dirs := $(chips_dir) res

LOCAL_EMMA_COVERAGE_FILTER := +com.android.calendar.*

LOCAL_MODULE_TAGS := optional

LOCAL_SRC_FILES := $(call all-java-files-under,src)

# bundled
#LOCAL_STATIC_JAVA_LIBRARIES += \
#		android-common \
#		android-common-chips \
#		calendar-common

# unbundled
LOCAL_STATIC_JAVA_LIBRARIES := \
		android-common \
		android-common-chips \
		calendar-common
LOCAL_SDK_VERSION := current

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, $(res_dirs))

LOCAL_PACKAGE_NAME := Calendar

LOCAL_PROGUARD_FLAG_FILES := proguard.flags


include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
include $(call all-makefiles-under,$(LOCAL_PATH))
