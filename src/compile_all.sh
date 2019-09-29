#!/bin/sh
if ! [ -d classes ];
then
   mkdir classes
fi
javac -cp . -d classes ./ir/*.java