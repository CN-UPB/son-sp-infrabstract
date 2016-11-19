#!/usr/bin/bash

packagename=${PWD##*/}

echo "Create $packagename.zip with following folders: META-INFO function_descriptors service_descriptors"

zip -r "$packagename.zip" META-INF function_descriptors service_descriptors

echo "Rename $packagename.zip to $packagename.son"

mv -n "$packagename.zip" "$packagename.son"
