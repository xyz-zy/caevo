echo -n "" > "cmds.txt"
for d in $1/*/; do
  echo "./rundistant.sh ${d}" >> "cmds.txt" 
done

