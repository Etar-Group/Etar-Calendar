LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := Calendar
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Calendar

canedar_root  := $(LOCAL_PATH)
canedar_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
canedar_build := $(canedar_root)/build
canedar_apk   := build/outputs/apk/Calendar-release-unsigned.apk

$(canedar_root)/$(canedar_apk):
	rm -Rf $(canedar_build)
	mkdir -p $(canedar_out)
	ln -s $(canedar_out) $(canedar_build)
	echo "sdk.dir=$(ANDROID_HOME)" > $(canedar_root)/local.properties
	cd $(canedar_root) && git submodule update --recursive --init
	cd $(canedar_root) && gradle dependencies && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" gradle assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(canedar_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)
LOCAL_MODULE_PATH := $(TARGET_OUT_DATA)/app

include $(BUILD_PREBUILT)
