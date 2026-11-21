@echo off
REM Usage: run.bat [web [port] ^| demo]      Needs only a JDK 17+ (no Maven).
REM Optional: put sqlite-jdbc-3.36.0.3.jar in the lib folder to store data in a SQLite database.
if not exist out mkdir out
if not exist lib mkdir lib
javac -encoding UTF-8 -d out -sourcepath src\main\java src\main\java\com\iitsm\Main.java
if errorlevel 1 exit /b 1
java -cp "out;lib\*" com.iitsm.Main %*
