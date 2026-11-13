cmake_minimum_required(VERSION 3.22)
project(combatstate LANGUAGES CXX)

add_library(combatstate SHARED CombatState.cpp)

target_compile_features(combatstate PRIVATE cxx_std_20)
target_compile_options(combatstate PRIVATE
        -fvisibility=hidden
        -fvisibility-inlines-hidden)
target_link_options(combatstate PRIVATE -Wl,-z,max-page-size=16384)
