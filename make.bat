xas99.py src/main-memory.a99 -i -q -R -L trapdoor.lst -E symbols.txt -o bin/main
xas99.py src/cartridge-banks.a99 -B -q -R -o bin/banks.bin

java -jar tools/ea5tocart.jar bin\main "THROUGH THE TRAPDOOR"

copy /b bin\main8.bin ^
    + bin\banks.bin ^
    trapdoor8.bin
