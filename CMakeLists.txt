cmake_minimum_required(VERSION 3.0)
project(VulkanMemoryAllocator-Hpp)

add_library(vma-hpp INTERFACE)
target_include_directories(vma-hpp INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/include")
