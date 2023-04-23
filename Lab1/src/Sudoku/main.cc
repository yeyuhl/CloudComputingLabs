#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/time.h>
#include <thread>
#include <pthread.h>
#include <iostream>
#include <fstream>
#include <string>
#include <cstring>

#include "sudoku.h"
using namespace std;

const int maxSudokuNum = 1024; // 数独数量上限
const int maxThreadNum = 200;   // 线程数量上限
int threadNum = 0;			   // 线程数
int **sudoku;				   // 存储多个数独的数组
bool *finished;				   // 已经解决的数独
thread threads[maxThreadNum];  // 线程数组
char puzzle[128];			   // 单一数独

// 统计时间的函数
int64_t now()
{
	struct timeval tv;
	gettimeofday(&tv, NULL);
	return tv.tv_sec * 1000000 + tv.tv_usec;
}

// 初始化函数
void init()
{
	// 开辟空间
	sudoku = new int *[maxSudokuNum];
	finished = new bool[maxSudokuNum];
	for (int i = 0; i < maxSudokuNum; i++)
		sudoku[i] = new int[N];
	threadNum = thread::hardware_concurrency(); // 用来获取电脑线程数，从而决定创建多少个线程，实际运用中感觉没啥用
}

// 将记录已解决数独的数组重置
void resetFinished()
{
	for (int i = 0; i < maxSudokuNum; i++)
	{
		finished[i] = false;
	}
}

// 每个线程要解决的数独
void solving(const int curr, const int sudokuNum)
{
	for (int i = 0; i < sudokuNum; ++i)
	{
		finished[curr + i] = solve_sudoku_dancing_links(sudoku[curr + i]);
	}
}

void solveOneFile(string file)
{
	ifstream inputFile(file, ios::in);
	if (!inputFile.is_open())
	{
		return;
	}
	resetFinished();
	bool endOfFile = false;
	// 读取文件
	while (!endOfFile)
	{
		int puzzleCount = 0; // 计算数独数量
		do
		{
			if (inputFile.eof())
			{
				endOfFile = true;
				break;
			}
			inputFile.getline(puzzle, N + 1);
			// 获取一个数独
			if (strlen(puzzle) >= N)
			{
				for (int i = 0; i < N; i++)
				{
					sudoku[puzzleCount][i] = puzzle[i] - 48; // char转int
				}
				puzzleCount++;
			}
		} while (puzzleCount < maxSudokuNum - 1);

		// step和curr用来控制打印顺序，让每个线程把正确顺序的sudoku交给算法解决
		int step = (puzzleCount + threadNum - 1) / threadNum, curr = 0;
		for (int i = 0; i < threadNum; ++i, curr += step)
		{
			threads[i] = thread(solving, curr, ((curr + step >= puzzleCount) ? (puzzleCount - curr) : step)); // 往solving函数传入两个参数，第二个参数需要判断
		}
		// 等待所有子线程完成
		for (int i = 0; i < threadNum; ++i)
			threads[i].join();

		// 把缓冲区里的sudoku输出
		for (int i = 0; i < puzzleCount; ++i)
		{
			if (finished[i])
			{
				for (int j = 0; j < N; j++)
					putchar('0' + sudoku[i][j]);
				putchar('\n');
			}
			else
				puts("No result.");
		}
	}
	inputFile.close();
}

// 释放空间
void freeSpace()
{
	for (int i = 0; i < maxSudokuNum; ++i)
		delete[] sudoku[i];
	delete[] sudoku;
}

int main()
{
	init();
	string file;
	int64_t start = now();
	while (getline(cin, file))
	{
		solveOneFile(file);
	}
	int64_t end = now();
	// double sec = (end-start)/1000000.0;
	// printf("%f sec %f ms \n", sec , 1000 * sec);
	freeSpace();
	return 0;
}
