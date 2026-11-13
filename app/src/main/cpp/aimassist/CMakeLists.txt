cmake_minimum_required(VERSION 3.22)
project(aimassist LANGUAGES CXX)

add_library(aimassist SHARED
        aimassist.cpp
        AimAssist.cpp
        AntiBot.cpp
        ClientBridge.cpp
        Esp.cpp
        Fullbright.cpp
        ../common/GameEcs.cpp)

target_compile_features(aimassist PRIVATE cxx_std_20)
target_compile_options(aimassist PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden)
target_include_directories(aimassist PRIVATE
        ../common
        ../combatstate
        ../third_party)
target_compile_definitions(aimassist PRIVATE
        ENTT_SPARSE_PAGE=2048
        ENTT_PACKED_PAGE=128)

target_link_libraries(aimassist PRIVATE preloader combatstate log dl)
target_link_options(aimassist PRIVATE -Wl,-z,max-page-size=16384)
