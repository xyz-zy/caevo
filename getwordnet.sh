#!/bin/bash
#
# 1. Downloads WordNet data files.
# 2. Creates a jwnl_file_properties.xml file to point to the downloaded data files.
#

BASE_DIR=.

mkdir $BASE_DIR/wordnet3.1

# Create the WordNet properties file.
cp src/test/resources/jwnl_file_properties.xml $BASE_DIR/wordnet3.1/
pushd $BASE_DIR/wordnet3.1

# Get the WordNet dictionary files.
# Use curl to download, as OS X doesn't include wget
wget http://wordnetcode.princeton.edu/wn3.1.dict.tar.gz
tar -xf wn3.1.dict.tar.gz

rm wn3.1.dict.tar.gz

# Put the current path into the properties file.
MYPWD=`pwd`
sed 's@src/test/resources/wordnet@'"$MYPWD"'@' jwnl_file_properties.xml > temp.txt
mv temp.txt jwnl_file_properties.xml

popd

echo "A new directory was created at $MYPWD"
echo "YOU MUST create an environment variable JWNL that points to $MYPWD/jwnl_file_properties.xml"
echo "Recommdation: add it to your .bashrc"

