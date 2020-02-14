#!/bin/bash
OUT_DIR="data_02132020/done/"
FILE_DIR="${1}/*"
for f in $FILE_DIR
do
  if [[ ${f: -4} != ".xml"  && ${f: -4} != "ents" ]]; then
    if test ! -f "${f}.info.xml"; then
      echo "Processing $f file..."
      ./runcaevoraw.sh $f
    fi
  fi
done

mv $1 $OUT_DIR

