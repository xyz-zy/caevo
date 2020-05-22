echo -n "" > $2
for d in $1/*/; do
  echo "./rundistant.sh ${d}" >> $2 
done

