package io.github.yeyuhl.raftkv.client;

import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.util.Scanner;
import java.util.UUID;

/**
 * Raft客户端
 */
@Slf4j
public class RaftClient {
    public static void main(String[] args) throws Throwable {
        RaftClientHandler client = new RaftClientHandler();
        InetAddress localHost = InetAddress.getLocalHost();
        String prefix = localHost.getHostAddress() + ":" + UUID.randomUUID().toString().substring(0, 5) + ":";
        int count = 0;

        // 从键盘接收数据
        Scanner scan = new Scanner(System.in);

        // nextLine方式接收字符串
        System.out.println("Raft client is running, please input command:");

        while (scan.hasNextLine()) {
            String input = scan.nextLine();
            if (input.equals("exit")) {
                scan.close();
                return;
            }
            String[] raftArgs = input.split(" ");
            int n = raftArgs.length;

            // 如果输入的参数不是2个或者3个，那么就是无效的输入
            if (n != 2 && n != 3) {
                System.out.println("invalid input");
                continue;
            }

            // get [key]
            if (n == 2) {
                if (!raftArgs[0].equals("get") && !raftArgs[0].equals("GET")) {
                    System.out.println("invalid input");
                    continue;
                }
                System.out.println(client.get(raftArgs[1], prefix + count++));
            }

            // [op] [key] [value]
            if (n == 3) {
                if (raftArgs[0].equals("put") || raftArgs[0].equals("PUT")) {
                    System.out.println(client.put(raftArgs[1], raftArgs[2], prefix + count++));
                } else if (raftArgs[0].equals("delete") || raftArgs[0].equals("DELETE")) {
                    System.out.println(client.del(raftArgs[1], raftArgs[2], prefix + count++));
                } else {
                    System.out.println("invalid input");
                }
            }
        }
    }
}
