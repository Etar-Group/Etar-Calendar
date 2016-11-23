LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := Calendar
LOCAL_MODULE_TAGS := optional
LOCAL_PACKAGE_NAME := Calendar

calendar_root  := $(LOCAL_PATH)
calendar_out   := $(OUT_DIR)/target/common/obj/APPS/$(LOCAL_MODULE)_intermediates
calendar_build := $(calendar_root)/build
calendar_apk   := build/outputs/apk/Calendar-release-unsigned.apk

$(calendar_root)/$(calendar_apk):
	rm -Rf $(calendar_build)
	mkdir -p $(calendar_out)
	ln -s $(calendar_out) $(calendar_build)
	echo "sdk.dir=$(ANDROID_HOME)" > $(calendar_root)/local.properties
	cd $(calendar_root) && git submodule update --recursive --init
	cd $(calendar_root) && ./gradlew dependencies && JAVA_TOOL_OPTIONS="$(JAVA_TOOL_OPTIONS) -Dfile.encoding=UTF8" ./gradlew assembleRelease

LOCAL_CERTIFICATE := platform
LOCAL_SRC_FILES := $(calendar_apk)
LOCAL_MODULE_CLASS := APPS
LOCAL_MODULE_SUFFIX := $(COMMON_ANDROID_PACKAGE_SUFFIX)

include $(BUILD_PREBUILT)
