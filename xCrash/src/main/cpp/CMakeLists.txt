cmake_minimum_required(VERSION 4.1.2)

project(xcrash LANGUAGES C)

#######################################
# global
#######################################

add_compile_options(
        -std=c11
        -Weverything
        -Werror
        -Wno-declaration-after-statement
        -Wno-strict-prototypes
        # NDK r28 (clang 19) 新增/收紧的警告
        -Wno-reserved-identifier
        -Wno-implicit-int-float-conversion
        -Wno-switch-default
        -Wno-unused-but-set-variable
        -Wno-implicit-fallthrough)

# -Wunsafe-buffer-usage 自 clang 18 起才支持，老版本 clang 不认识该选项会触发 -Werror
if(CMAKE_C_COMPILER_VERSION VERSION_GREATER_EQUAL 18)
    add_compile_options(-Wno-unsafe-buffer-usage)
endif()

# clang 21 (NDK r30) 新增的 C++ 兼容性警告，-Weverything 会启用它们
if(CMAKE_C_COMPILER_VERSION VERSION_GREATER_EQUAL 21)
    add_compile_options(
            -Wno-jump-bypasses-init
            -Wno-implicit-void-ptr-cast)
endif()

#######################################
# libCrash.so
#######################################

file(GLOB XCRASH_SRC
        xcrash/*.c
        common/*.c
        dl/*.c)

add_library(xcrash SHARED
        ${XCRASH_SRC})

# 产物文件名: libCrash.so (Java 侧 System.loadLibrary("Crash"))
set_target_properties(xcrash PROPERTIES OUTPUT_NAME "Crash")

target_include_directories(xcrash PUBLIC
        xcrash
        xcrash_dumper
        common
        dl)

target_link_libraries(xcrash
        log
        dl)

if(USEASAN)

target_compile_options(xcrash PUBLIC
        -fsanitize=address
        -fno-omit-frame-pointer)

set_target_properties(xcrash PROPERTIES
        LINK_FLAGS " \
        -fsanitize=address")

else()

target_compile_options(xcrash PUBLIC
        -Oz
        -flto
        -ffunction-sections
        -fdata-sections)

set_target_properties(xcrash PROPERTIES
        LINK_FLAGS " \
        -O3 \
        -flto \
        -Wl,--exclude-libs,ALL \
        -Wl,--gc-sections \
        -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/xcrash.exports")

endif()

#######################################
# libCrash_dumper.so
#######################################

file(GLOB XCRASH_DUMPER_SRC
        xcrash_dumper/*.c
        common/*.c)

set(LZME_SRC
        lzma/7zCrc.c
        lzma/7zCrcOpt.c
        lzma/CpuArch.c
        lzma/Bra.c
        lzma/Bra86.c
        lzma/BraIA64.c
        lzma/Delta.c
        lzma/Lzma2Dec.c
        lzma/LzmaDec.c
        lzma/Sha256.c
        lzma/Xz.c
        lzma/XzCrc64.c
        lzma/XzCrc64Opt.c
        lzma/XzDec.c)

set_source_files_properties(${LZME_SRC} PROPERTIES
        COMPILE_FLAGS " \
        -D_7ZIP_ST \
        -Wno-enum-conversion \
        -Wno-reserved-id-macro \
        -Wno-undef \
        -Wno-missing-prototypes \
        -Wno-missing-variable-declarations \
        -Wno-cast-align \
        -Wno-sign-conversion \
        -Wno-assign-enum \
        -Wno-unused-macros \
        -Wno-padded \
        -Wno-cast-qual \
        -Wno-strict-prototypes \
        -Wno-extra-semi-stmt")

add_executable(xcrash_dumper
        ${XCRASH_DUMPER_SRC}
        ${LZME_SRC})

target_include_directories(xcrash_dumper PUBLIC
        xcrash_dumper
        common
        lzma)

target_link_libraries(xcrash_dumper
        log
        dl)

if(USEASAN)

target_compile_options(xcrash_dumper PUBLIC
        -fsanitize=address
        -fno-omit-frame-pointer)

set_target_properties(xcrash_dumper PROPERTIES
        LINK_FLAGS " \
        -fsanitize=address")

else()

target_compile_options(xcrash_dumper PUBLIC
        -Oz
        -flto
        -ffunction-sections
        -fdata-sections)

set_target_properties(xcrash_dumper PROPERTIES
        LINK_FLAGS " \
        -O3 \
        -flto \
        -Wl,--exclude-libs,ALL \
        -Wl,--gc-sections")

endif()

set_target_properties(xcrash_dumper PROPERTIES
        OUTPUT_NAME "Crash_dumper"
        PREFIX "lib"
        SUFFIX ".so")
