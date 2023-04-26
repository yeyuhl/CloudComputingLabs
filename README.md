# Cloud Computing:实验介绍
[Lab1](Lab1/README.md)

解数独，从stdin中读取要解数独文件的名字，然后交给多个线程来填数独（一个文件里面有多个数独问题），然后将填完的数独按照文件中的顺序输出到stdout，实验一的重点就在于多线程并发编程。

[Lab2](Lab2/README.md)

写一个简单的httpserver，懒得写得太复杂，用Netty写了一个base版本，跑助教的tester程序的时候记得要把config改一下，并且安装jdk1.8，不然没法运行。此外由于是用Netty写的，所以如果创建NioEventLoopGroup时没有指定线程数量，那么Netty会使用默认的线程数量，即CPU核心数的2倍。

[Lab3](Lab3/README.md)

[Lab4](Lab4/README.md) 
