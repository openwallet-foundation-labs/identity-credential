cmake_minimum_required(VERSION 3.16)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED True)

add_library(
        MatcherTest
        SHARED
        MatcherTest.cpp
        ../../androidMain/matcher/Request.cpp
        ../../androidMain/matcher/CredentialDatabase.cpp
        ../../androidMain/matcher/cppbor.cpp
        ../../androidMain/matcher/cppbor_parse.cpp
        ../../androidMain/matcher/cJSON.c
        ../../androidMain/matcher/matcher.cpp
)
