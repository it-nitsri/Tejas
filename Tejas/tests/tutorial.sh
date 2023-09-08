g++ -o hello.o -static hello_world.cpp

java -jar <path to tejas/jars/tejas.jar> <path to config file> <path to stat out file> <input executable>

#kill -9 $(ps -aux | grep <name of the executable> | grep -v "grep" | awk '{print $2}')
kill -9 $(ps -aux | grep "hello.o" | grep -v "grep" | awk '{print $2}')

