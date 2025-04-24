проба пера )


mvn clean javafx:run 
make run



cd ~/Downloads/LogParser.app/Contents/MacOS
./LogParser

// structure
tree src/main

// all files
find src -type f | while read file; do
echo "=== $file ==="
cat "$file"
done