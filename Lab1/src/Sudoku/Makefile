CXXFLAGS+=-O2 -ggdb -DDEBUG
CXXFLAGS+=-Wall -Wextra

.PHONY : clean

all: sudoku_solve

sudoku_solve: main.cc sudoku_dancing_links.cc
	      g++ -std=c++11 -O2 -o $@ $^ -lpthread

clean:
	-rm sudoku_solve
