# cs5223

* Compile: 
mkdir bin
javac -d bin src/*.java

* Run Tracker:
cd bin
java Tracker <port> <N> <K> # example: java Tracker 55555 10 20

* Run Player:
cd bin
java Game <tracker ip> <tracker port> <player id> # example: java Game 127.0.0.1 55555 aa

