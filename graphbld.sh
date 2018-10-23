#!/bin/bash

if [ $# -lt 6 ]
then
        echo "Usage: $0 <input text file (each line comprising tweet-id, cluster-id, similarity with centroid, epochs and text (separated by tabs)> <mode (tempo (1) /lexical (2) / tempo-lexical (3))> <head %ge> <tail %ge> (the last two for pruning vocabulary) <out file> <number of time intervals>"
        exit
fi

#the i/p file needs to be sorted by time
cat $1 | sort -n -k4 > tmp
mv tmp $1

mvn exec:java@graphbuild -Dexec.args="$1 $2 $3 $4 $5 $6"

cat $5 | sort -nr -k3 > $5.sorted
cat $5.sorted | awk '{if (NR==1) {print $1 "\t" $2 "\t" $3/$3; max=$3} else {print $1 "\t" $2 "\t" $3/max}}' > tmp
mv tmp $5.sorted

