#!/bin/sh
# Usage: ./run.sh [web [port] | demo]      Needs only a JDK 17+ (no Maven).
# Optional: put sqlite-jdbc-3.36.0.3.jar in lib/ to store data in a SQLite database.
mkdir -p out lib && javac -encoding UTF-8 -d out -sourcepath src/main/java src/main/java/com/iitsm/Main.java && java -cp "out:lib/*" com.iitsm.Main "$@"
