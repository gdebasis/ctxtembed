#!/bin/bash

if [ $# -lt 1 ]
then
        echo "Usage: $0 <input json file> (automatically detects file type - a json file needs to have the extension .json)"
        exit
fi

mvn exec:java@preprocess -Dexec.args="$1"

