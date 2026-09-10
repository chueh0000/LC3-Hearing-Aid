cmake_minimum_required(VERSION 3.22.1)

project("hearingaid")

# Add oboe from the prefab directory
find_package(oboe REQUIRED CONFIG)

add_library(
        hearingaid
        SHARED
        native-lib.cpp
        AudioEngine.cpp
        DspFilters.cpp)

target_link_libraries(
        hearingaid
        oboe::oboe
        android
        log)
