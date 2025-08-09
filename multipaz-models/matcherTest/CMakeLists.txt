cmake_minimum_required(VERSION 3.16)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

add_library(
        MatcherTest
        SHARED
        MatcherTest.cpp
        ../src/androidMain/matcher/Request.cpp
        ../src/androidMain/matcher/CredentialDatabase.cpp
        ../src/androidMain/matcher/cppbor.cpp
        ../src/androidMain/matcher/cppbor_parse.cpp
        ../src/androidMain/matcher/cJSON.c
        ../src/androidMain/matcher/matcher.cpp
        ../src/androidMain/matcher/logger.cpp
        ../src/androidMain/matcher/dcql.cpp
)

target_link_libraries(MatcherTest android log)

target_compile_definitions(MatcherTest PRIVATE MATCHER_TEST_BUILD)