cmake_minimum_required(VERSION 3.22.1)

project("vfs_core")

add_library(
    vfs_core
    SHARED
    src/main/cpp/vfs_core.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    vfs_core
    ${log-lib}
)
