cmake_minimum_required(VERSION 3.22)
project(movementmod LANGUAGES CXX)

add_library(movementmod SHARED movement.cpp ../common/GameEcs.cpp)

target_compile_features(movementmod PRIVATE cxx_std_20)
target_compile_options(movementmod PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden)
target_include_directories(movementmod PRIVATE
        ../common
        ../third_party)
target_compile_definitions(movementmod PRIVATE
        ENTT_SPARSE_PAGE=2048
        ENTT_PACKED_PAGE=128)

target_link_libraries(movementmod PRIVATE preloader log dl)
target_link_options(movementmod PRIVATE -Wl,-z,max-page-size=16384)
