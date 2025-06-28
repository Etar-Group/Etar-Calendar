# TODO(b/198224074): auto-generate this file using bp2build.
load("@rules_android//rules:rules.bzl", "android_library")
load("//build/bazel/rules/java:event_log_tags.bzl", "event_log_tags")

event_log_tags(
    name = "genlogtags",
    srcs = glob(["java/**/*.logtags"]),
)

android_library(
    name = "android-common",
    srcs = glob([
        "java/**/*.java",
    ]),
    custom_package = "com.android.common",
    manifest = "AndroidManifest.xml",
    visibility = ["//visibility:public"],
    deps = ["//prebuilts/sdk:public_current_android_sdk_java_import"],
)
