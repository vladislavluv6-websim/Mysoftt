cmake_minimum_required(VERSION 3.22)
project(speedmod LANGUAGES CXX)

add_library(speedmod SHARED speedmod.cpp ../common/GameEcs.cpp)

target_compile_features(speedmod PRIVATE cxx_std_20)
target_compile_options(speedmod PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden)
target_include_directories(speedmod PRIVATE
        ../common
        ../combatstate
        ../third_party)
target_compile_definitions(speedmod PRIVATE
        ENTT_SPARSE_PAGE=2048
        ENTT_PACKED_PAGE=128)

target_link_libraries(speedmod PRIVATE preloader combatstate log dl)
target_link_options(speedmod PRIVATE -Wl,-z,max-page-size=16384)
